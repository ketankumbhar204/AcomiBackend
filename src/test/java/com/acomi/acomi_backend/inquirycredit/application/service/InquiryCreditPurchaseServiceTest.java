package com.acomi.acomi_backend.inquirycredit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
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
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class InquiryCreditPurchaseServiceTest {

    @Mock
    private InquiryCreditPurchaseRequestRepository purchaseRepository;

    @Mock
    private InquiryCreditPackageRepository packageRepository;

    @Mock
    private InquiryPaymentConfigRepository paymentConfigRepository;

    @Mock
    private InquiryCreditWalletService walletService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    private InquiryCreditPurchaseService purchaseService;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private UUID userId;
    private UUID packageId;
    private UUID requestId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        purchaseService = new InquiryCreditPurchaseService(
                purchaseRepository,
                packageRepository,
                paymentConfigRepository,
                walletService,
                notificationService,
                userRepository,
                fixedClock);
        userId = UUID.randomUUID();
        packageId = UUID.randomUUID();
        requestId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @Test
    void createRequest_whenNoPendingExists_createsNewRequest() {
        stubPaymentEnabled(true);
        InquiryCreditPackageEntity pkg = buildPackage(packageId, 30, new BigDecimal("9.00"));
        when(purchaseRepository.findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
                        userId, packageId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.empty());
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        when(userRepository.findBySystemRoleAndIsActiveTrue(any())).thenReturn(List.of());

        InquiryCreditPurchaseRequestEntity saved =
                buildRequest(requestId, userId, packageId, 30, InquiryCreditPurchaseStatus.PENDING);
        when(purchaseRepository.save(any())).thenReturn(saved);

        InquiryCreditPurchaseRequestResponse response =
                purchaseService.createRequest(userId, packageId, "UTR123");

        assertThat(response.getStatus()).isEqualTo(InquiryCreditPurchaseStatus.PENDING);
        assertThat(response.getCredits()).isEqualTo(30);
        verify(purchaseRepository, times(1)).save(any());
    }

    @Test
    void createRequest_whenPaymentDisabled_throws() {
        stubPaymentEnabled(false);

        assertThatThrownBy(() -> purchaseService.createRequest(userId, packageId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("INQUIRY_PAYMENT_DISABLED");
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void createRequest_whenPendingExists_returnsExisting() {
        InquiryCreditPurchaseRequestEntity existing =
                buildRequest(requestId, userId, packageId, 30, InquiryCreditPurchaseStatus.PENDING);
        when(purchaseRepository.findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
                        userId, packageId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.of(existing));

        InquiryCreditPurchaseRequestResponse response =
                purchaseService.createRequest(userId, packageId, null);

        assertThat(response.getId()).isEqualTo(requestId);
        verify(purchaseRepository, never()).save(any());
        verify(paymentConfigRepository, never()).findFirstByOrderByCreatedAtAsc();
    }

    @Test
    void createRequest_whenPackageDisabled_throwsNotFound() {
        stubPaymentEnabled(true);
        when(purchaseRepository.findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
                        userId, packageId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.empty());
        when(packageRepository.findById(packageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.createRequest(userId, packageId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo("INQUIRY_PACKAGE_UNAVAILABLE"));
    }

    @Test
    void createRequest_whenAdminExists_publishesPlatformNotificationWithoutSpaceId() {
        stubPaymentEnabled(true);
        InquiryCreditPackageEntity pkg = buildPackage(packageId, 30, new BigDecimal("9.00"));
        when(purchaseRepository.findFirstByUserIdAndPackageIdAndStatusOrderByRequestedAtDesc(
                        userId, packageId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.empty());
        when(packageRepository.findById(packageId)).thenReturn(Optional.of(pkg));
        UserEntity admin = new UserEntity();
        admin.setId(adminId);
        admin.setSystemRole(SystemRole.ADMIN);
        when(userRepository.findBySystemRoleAndIsActiveTrue(any())).thenReturn(List.of(admin));
        InquiryCreditPurchaseRequestEntity saved =
                buildRequest(requestId, userId, packageId, 30, InquiryCreditPurchaseStatus.PENDING);
        when(purchaseRepository.save(any())).thenReturn(saved);

        purchaseService.createRequest(userId, packageId, "UTR123");

        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        PublishNotificationCommand cmd = captor.getValue();
        assertThat(cmd.getSpaceId()).isNull();
        assertThat(cmd.getEntityType()).isEqualTo(NotificationEntityType.INQUIRY_CREDIT_PURCHASE);
        assertThat(cmd.getNotificationType()).isEqualTo(NotificationType.INQUIRY_CREDIT_PAYMENT_PENDING);
        assertThat(cmd.getUserId()).isEqualTo(adminId);
        assertThat(cmd.getEntityId()).isEqualTo(requestId);
    }

    @Test
    void approve_whenPending_publishesUserNotificationWithoutSpaceId() {
        InquiryCreditPurchaseRequestEntity request =
                buildRequest(requestId, userId, packageId, 30, InquiryCreditPurchaseStatus.PENDING);
        when(purchaseRepository.findByIdAndStatusForUpdate(requestId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.of(request));
        when(purchaseRepository.save(any())).thenReturn(request);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        purchaseService.approve(requestId, adminId);

        verify(walletService, times(1)).grantPurchase(userId, 30, requestId, adminId);
        ArgumentCaptor<PublishNotificationCommand> captor =
                ArgumentCaptor.forClass(PublishNotificationCommand.class);
        verify(notificationService).publish(captor.capture());
        PublishNotificationCommand cmd = captor.getValue();
        assertThat(cmd.getSpaceId()).isNull();
        assertThat(cmd.getEntityType()).isEqualTo(NotificationEntityType.INQUIRY_CREDIT_PURCHASE);
        assertThat(cmd.getNotificationType()).isEqualTo(NotificationType.INQUIRY_CREDIT_PAYMENT_APPROVED);
        assertThat(cmd.getUserId()).isEqualTo(userId);
    }


    @Test
    void approve_whenNotPending_throwsConflict() {
        when(purchaseRepository.findByIdAndStatusForUpdate(requestId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.approve(requestId, adminId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void approve_calledTwice_grantedOnlyOnce() {
        InquiryCreditPurchaseRequestEntity request =
                buildRequest(requestId, userId, packageId, 30, InquiryCreditPurchaseStatus.PENDING);
        when(purchaseRepository.findByIdAndStatusForUpdate(requestId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.of(request))
                .thenReturn(Optional.empty());
        when(purchaseRepository.save(any())).thenReturn(request);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        purchaseService.approve(requestId, adminId);

        assertThatThrownBy(() -> purchaseService.approve(requestId, adminId))
                .isInstanceOf(BusinessException.class);

        verify(walletService, times(1)).grantPurchase(eq(userId), eq(30), eq(requestId), eq(adminId));
    }

    @Test
    void reject_whenPending_rejectsWithReason() {
        InquiryCreditPurchaseRequestEntity request =
                buildRequest(requestId, userId, packageId, 30, InquiryCreditPurchaseStatus.PENDING);
        when(purchaseRepository.findByIdAndStatusForUpdate(requestId, InquiryCreditPurchaseStatus.PENDING))
                .thenReturn(Optional.of(request));
        when(purchaseRepository.save(any())).thenReturn(request);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        InquiryCreditPurchaseRequestResponse response =
                purchaseService.reject(requestId, adminId, "Invalid payment");

        assertThat(response.getStatus()).isEqualTo(InquiryCreditPurchaseStatus.REJECTED);
        verify(walletService, never()).grantPurchase(any(), any(Integer.class), any(), any());
    }

    private void stubPaymentEnabled(boolean enabled) {
        InquiryPaymentConfigEntity config = InquiryPaymentConfigEntity.builder()
                .enabled(enabled)
                .build();
        config.setId(UUID.randomUUID());
        when(paymentConfigRepository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(config));
    }

    private InquiryCreditPackageEntity buildPackage(UUID id, int credits, BigDecimal price) {
        InquiryCreditPackageEntity pkg = InquiryCreditPackageEntity.builder()
                .name("Test Package")
                .priceAmount(price)
                .currency("INR")
                .credits(credits)
                .enabled(true)
                .displayOrder(0)
                .clientChannel(com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel.WEB)
                .build();
        pkg.setId(id);
        return pkg;
    }

    private InquiryCreditPurchaseRequestEntity buildRequest(
            UUID id, UUID userId, UUID packageId, int credits, InquiryCreditPurchaseStatus status) {
        InquiryCreditPurchaseRequestEntity entity = InquiryCreditPurchaseRequestEntity.builder()
                .userId(userId)
                .packageId(packageId)
                .amount(new BigDecimal("9.00"))
                .currency("INR")
                .credits(credits)
                .paymentMethod("UPI_MANUAL")
                .status(status)
                .requestedAt(LocalDateTime.now(fixedClock))
                .build();
        entity.setId(id);
        return entity;
    }
}
