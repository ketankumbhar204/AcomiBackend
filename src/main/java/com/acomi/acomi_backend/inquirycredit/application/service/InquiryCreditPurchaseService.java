package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryAdminSummaryResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPurchaseRequestResponse;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditPurchaseStatus;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPackageEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPurchaseRequestEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPackageRepository;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPurchaseRequestRepository;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryPaymentConfigRepository;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryCreditPurchaseService {

    private final InquiryCreditPurchaseRequestRepository purchaseRepository;
    private final InquiryCreditPackageRepository packageRepository;
    private final InquiryPaymentConfigRepository paymentConfigRepository;
    private final InquiryCreditWalletService walletService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Create a purchase request, or return the existing PENDING one for the same user + package.
     */
    @Transactional
    public InquiryCreditPurchaseRequestResponse createRequest(UUID userId, UUID packageId, String utr) {
        InquiryCreditPurchaseRequestEntity existing = purchaseRepository
                .findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
                        userId, packageId, InquiryCreditPurchaseStatus.PENDING)
                .orElse(null);
        if (existing != null) {
            log.info(
                    "inquiry_purchase_reuse existingId={} userId={} packageId={}",
                    existing.getId(),
                    userId,
                    packageId);
            return InquiryCreditPurchaseRequestResponse.from(existing);
        }

        requirePaymentEnabled();

        InquiryCreditPackageEntity pkg = packageRepository
                .findById(packageId)
                .filter(InquiryCreditPackageEntity::isEnabled)
                .orElseThrow(() -> new BusinessException(
                        "INQUIRY_PACKAGE_UNAVAILABLE",
                        "The selected credit package is not available.",
                        HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        InquiryCreditPurchaseRequestEntity request = InquiryCreditPurchaseRequestEntity.builder()
                .userId(userId)
                .packageId(packageId)
                .amount(pkg.getPriceAmount())
                .currency(pkg.getCurrency())
                .credits(pkg.getCredits())
                .paymentMethod("UPI_MANUAL")
                .status(InquiryCreditPurchaseStatus.PENDING)
                .utr(utr != null && !utr.isBlank() ? utr.trim() : null)
                .requestedAt(now)
                .build();

        try {
            request = purchaseRepository.save(request);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent create of PENDING for same user+package — reuse winner.
            InquiryCreditPurchaseRequestEntity raced = purchaseRepository
                    .findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
                            userId, packageId, InquiryCreditPurchaseStatus.PENDING)
                    .orElseThrow(() -> new BusinessException(
                            "INQUIRY_PURCHASE_CREATE_CONFLICT",
                            "Could not create purchase request. Please try again.",
                            HttpStatus.CONFLICT));
            log.info(
                    "inquiry_purchase_reuse_after_race existingId={} userId={} packageId={}",
                    raced.getId(),
                    userId,
                    packageId);
            return InquiryCreditPurchaseRequestResponse.from(raced);
        }

        notifyAdmins(request, userId);
        log.info(
                "inquiry_purchase_created requestId={} userId={} packageId={}",
                request.getId(),
                userId,
                packageId);
        return InquiryCreditPurchaseRequestResponse.from(request);
    }

    /**
     * Approve a PENDING purchase request and grant credits. Idempotent under concurrency.
     */
    @Transactional
    public InquiryCreditPurchaseRequestResponse approve(UUID requestId, UUID adminId) {
        InquiryCreditPurchaseRequestEntity request = purchaseRepository
                .findByIdAndStatusForUpdate(requestId, InquiryCreditPurchaseStatus.PENDING)
                .orElseThrow(() -> new BusinessException(
                        "INQUIRY_PURCHASE_NOT_PENDING",
                        "Purchase request is not in PENDING status.",
                        HttpStatus.CONFLICT));

        LocalDateTime now = LocalDateTime.now(clock);
        request.setStatus(InquiryCreditPurchaseStatus.APPROVED);
        request.setVerifiedAt(now);
        request.setVerifiedByUserId(adminId);
        purchaseRepository.save(request);

        walletService.grantPurchase(request.getUserId(), request.getCredits(), requestId, adminId);

        notifyUser(request, NotificationType.INQUIRY_CREDIT_PAYMENT_APPROVED);
        log.info("inquiry_purchase_approved requestId={} adminId={}", requestId, adminId);
        return InquiryCreditPurchaseRequestResponse.from(request, userRepository.findById(request.getUserId()).orElse(null));
    }

    /**
     * Reject a PENDING purchase request.
     */
    @Transactional
    public InquiryCreditPurchaseRequestResponse reject(UUID requestId, UUID adminId, String reason) {
        InquiryCreditPurchaseRequestEntity request = purchaseRepository
                .findByIdAndStatusForUpdate(requestId, InquiryCreditPurchaseStatus.PENDING)
                .orElseThrow(() -> new BusinessException(
                        "INQUIRY_PURCHASE_NOT_PENDING",
                        "Purchase request is not in PENDING status.",
                        HttpStatus.CONFLICT));

        LocalDateTime now = LocalDateTime.now(clock);
        request.setStatus(InquiryCreditPurchaseStatus.REJECTED);
        request.setVerifiedAt(now);
        request.setVerifiedByUserId(adminId);
        request.setRejectionReason(reason != null && !reason.isBlank() ? reason.trim() : null);
        purchaseRepository.save(request);

        notifyUser(request, NotificationType.INQUIRY_CREDIT_PAYMENT_REJECTED);
        log.info("inquiry_purchase_rejected requestId={} adminId={}", requestId, adminId);
        return InquiryCreditPurchaseRequestResponse.from(request, userRepository.findById(request.getUserId()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<InquiryCreditPurchaseRequestResponse> listForAdmin(
            InquiryCreditPurchaseStatus status, Pageable pageable) {
        Page<InquiryCreditPurchaseRequestEntity> page = status != null
                ? purchaseRepository.findByStatusOrderByRequestedAtDesc(status, pageable)
                : purchaseRepository.findAllByOrderByRequestedAtDesc(pageable);

        List<UUID> userIds = page.getContent().stream()
                .map(InquiryCreditPurchaseRequestEntity::getUserId)
                .distinct()
                .toList();
        Map<UUID, UserEntity> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        return page.map(entity -> InquiryCreditPurchaseRequestResponse.from(entity, users.get(entity.getUserId())));
    }

    @Transactional(readOnly = true)
    public List<InquiryCreditPurchaseRequestResponse> listMine(UUID userId) {
        return purchaseRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(InquiryCreditPurchaseRequestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InquiryAdminSummaryResponse adminSummary() {
        return InquiryAdminSummaryResponse.builder()
                .pendingCount(purchaseRepository.countByStatus(InquiryCreditPurchaseStatus.PENDING))
                .approvedCount(purchaseRepository.countByStatus(InquiryCreditPurchaseStatus.APPROVED))
                .rejectedCount(purchaseRepository.countByStatus(InquiryCreditPurchaseStatus.REJECTED))
                .build();
    }

    private void requirePaymentEnabled() {
        InquiryPaymentConfigEntity config = paymentConfigRepository
                .findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new BusinessException(
                        "INQUIRY_PAYMENT_CONFIG_MISSING",
                        "Inquiry payment configuration has not been initialized.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        if (!config.isEnabled()) {
            throw new BusinessException(
                    "INQUIRY_PAYMENT_DISABLED",
                    "Inquiry credit payment is temporarily unavailable.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void notifyAdmins(InquiryCreditPurchaseRequestEntity request, UUID actorId) {
        List<UserEntity> admins = userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN);
        for (UserEntity admin : admins) {
            // Inquiry-credit purchase is user/admin scoped — no Space. spaceId stays null.
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(null)
                    .userId(admin.getId())
                    .actorId(actorId)
                    .entityType(NotificationEntityType.INQUIRY_CREDIT_PURCHASE)
                    .entityId(request.getId())
                    .notificationType(NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING)
                    .category(NotificationCategory.ACTION_REQUIRED)
                    .priority(NotificationPriority.HIGH)
                    .title("New inquiry credit purchase request")
                    .message("A user has submitted a payment request for " + request.getCredits() + " inquiry credits.")
                    .actionLabel("Review request")
                    .actionRoute("AdminInquiryCreditRequests")
                    .dedupeKey("INQUIRY_CREDIT_PAYMENT_PENDING:" + request.getId() + ":" + admin.getId())
                    .build());
        }
        if (admins.isEmpty()) {
            log.warn("inquiry_purchase_no_admins_to_notify requestId={}", request.getId());
        }
    }

    private void notifyUser(InquiryCreditPurchaseRequestEntity request, NotificationType type) {
        String title;
        String message;
        if (type == NotificationType.INQUIRY_CREDIT_PAYMENT_APPROVED) {
            title = "Inquiry credits added";
            message = request.getCredits() + " inquiry credits have been added to your wallet.";
        } else {
            title = "Payment request rejected";
            message = "Your inquiry credit purchase request has been rejected."
                    + (request.getRejectionReason() != null ? " Reason: " + request.getRejectionReason() : "");
        }

        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(null)
                .userId(request.getUserId())
                .entityType(NotificationEntityType.INQUIRY_CREDIT_PURCHASE)
                .entityId(request.getId())
                .notificationType(type)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title(title)
                .message(message)
                .actionLabel("View wallet")
                .actionRoute("InquiryCredits")
                .dedupeKey(type.name() + ":" + request.getId())
                .build());
    }
}
