package com.acomi.acomi_backend.enquiry.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.enquiry.api.dto.request.CreateSpaceEnquiryRequest;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminEnquirySummaryResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminSpaceEnquiryDetailResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.AdminSpaceEnquiryListItemResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.OwnerContactResponse;
import com.acomi.acomi_backend.enquiry.api.dto.response.SpaceEnquiryResponse;
import com.acomi.acomi_backend.enquiry.domain.model.EnquiryRequesterType;
import com.acomi.acomi_backend.enquiry.domain.model.SpaceEnquiryStatus;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareDecision;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoSharePolicy;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.entity.SpaceEnquiryEntity;
import com.acomi.acomi_backend.enquiry.infrastructure.persistence.repository.SpaceEnquiryRepository;
import com.acomi.acomi_backend.mail.application.dto.SendEmailCommand;
import com.acomi.acomi_backend.mail.application.service.EmailService;
import com.acomi.acomi_backend.mail.application.support.EmailIdempotencyKeys;
import com.acomi.acomi_backend.mail.config.MailProperties;
import com.acomi.acomi_backend.mail.domain.model.EmailEventType;
import com.acomi.acomi_backend.notification.application.port.in.PublishNotificationCommand;
import com.acomi.acomi_backend.notification.application.service.NotificationService;
import com.acomi.acomi_backend.notification.domain.model.NotificationCategory;
import com.acomi.acomi_backend.notification.domain.model.NotificationEntityType;
import com.acomi.acomi_backend.notification.domain.model.NotificationPriority;
import com.acomi.acomi_backend.notification.domain.model.NotificationType;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SpaceEnquiryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SpaceEnquiryRepository enquiryRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final OwnerContactResolver ownerContactResolver;
    private final EnquiryListingDetailsResolver listingDetailsResolver;
    private final EnquiryAutoSharePolicy enquiryAutoSharePolicy;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final MailProperties mailProperties;
    private final Clock clock;
    private final int retentionDays;

    public SpaceEnquiryService(
            SpaceEnquiryRepository enquiryRepository,
            SpaceRepository spaceRepository,
            UserRepository userRepository,
            PropertyRegistrationRepository propertyRegistrationRepository,
            MessRegistrationRepository messRegistrationRepository,
            OwnerContactResolver ownerContactResolver,
            EnquiryListingDetailsResolver listingDetailsResolver,
            EnquiryAutoSharePolicy enquiryAutoSharePolicy,
            EmailService emailService,
            NotificationService notificationService,
            MailProperties mailProperties,
            Clock clock,
            @Value("${acomi.enquiry.retention-days:30}") int retentionDays) {
        this.enquiryRepository = enquiryRepository;
        this.spaceRepository = spaceRepository;
        this.userRepository = userRepository;
        this.propertyRegistrationRepository = propertyRegistrationRepository;
        this.messRegistrationRepository = messRegistrationRepository;
        this.ownerContactResolver = ownerContactResolver;
        this.listingDetailsResolver = listingDetailsResolver;
        this.enquiryAutoSharePolicy = enquiryAutoSharePolicy;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.mailProperties = mailProperties;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public SpaceEnquiryResponse create(UUID requesterId, UUID spaceId, CreateSpaceEnquiryRequest request) {
        UserEntity requester = userRepository
                .findByIdAndIsActiveTrue(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", requesterId));

        SpaceEntity space = spaceRepository
                .findByIdAndIsActiveTrueAndDiscoverableTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        if (spaceRepository.existsByIdAndOwnerIdAndIsActiveTrue(spaceId, requesterId)) {
            throw new BusinessException(
                    "SELF_ENQUIRY_NOT_ALLOWED",
                    "This is your Space. Contact enquiry isn't available for your own listing.",
                    HttpStatus.CONFLICT);
        }

        String email = resolveRequesterEmail(requester, request);
        requester.rememberEnquiryEmail(email);
        LocalDateTime now = LocalDateTime.now(clock);

        SpaceEnquiryEntity pending = enquiryRepository
                .findBySpaceIdAndRequesterUserIdAndStatus(spaceId, requesterId, SpaceEnquiryStatus.PENDING)
                .orElse(null);
        expireIfDue(pending, now);
        if (pending != null && pending.getStatus() == SpaceEnquiryStatus.PENDING) {
            return reuseExistingEnquiry(pending, space);
        }

        return enquiryRepository
                .findBySpaceIdAndRequesterUserIdAndStatus(spaceId, requesterId, SpaceEnquiryStatus.SHARED)
                .map(existing -> reuseExistingEnquiry(existing, space))
                .orElseGet(() -> createNewEnquiry(requester, space, email, now));
    }

    @Transactional
    public PagedResponse<SpaceEnquiryResponse> listMine(UUID requesterId, Pageable pageable) {
        expirePendingAndNotify(LocalDateTime.now(clock));
        Page<SpaceEnquiryEntity> page =
                enquiryRepository.findByRequesterUserIdOrderByRequestedAtDesc(requesterId, safePage(pageable));
        Map<UUID, SpaceEntity> spaces = loadSpaces(page.getContent());
        Map<UUID, EnquiryListingDetails> listings = new HashMap<>();
        for (SpaceEntity space : spaces.values()) {
            listings.put(space.getId(), listingDetailsResolver.resolve(space));
        }
        List<SpaceEnquiryResponse> items = page.getContent().stream()
                .map(entity -> toMemberResponse(
                        entity, spaces.get(entity.getSpaceId()), listings.get(entity.getSpaceId())))
                .toList();
        return PagedResponse.<SpaceEnquiryResponse>builder()
                .content(items)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public SpaceEnquiryResponse getMine(UUID requesterId, UUID enquiryId) {
        SpaceEnquiryEntity entity = enquiryRepository
                .findByIdAndRequesterUserId(enquiryId, requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", "id", enquiryId));
        expireIfDue(entity, LocalDateTime.now(clock));
        SpaceEntity space = spaceRepository.findById(entity.getSpaceId()).orElse(null);
        EnquiryListingDetails listing = space == null ? EnquiryListingDetails.empty() : listingDetailsResolver.resolve(space);
        return toMemberResponse(entity, space, listing);
    }

    @Transactional
    public PagedResponse<AdminSpaceEnquiryListItemResponse> listForAdmin(
            SpaceEnquiryStatus status, Pageable pageable) {
        return listForAdmin(status, null, null, null, null, pageable);
    }

    @Transactional
    public PagedResponse<AdminSpaceEnquiryListItemResponse> listForAdmin(
            SpaceEnquiryStatus status,
            EnquiryRequesterType requesterType,
            String query,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        expirePendingAndNotify(LocalDateTime.now(clock));
        Pageable safe = safePage(pageable);
        String q = query == null ? null : query.trim();
        if (q != null && q.isEmpty()) {
            q = null;
        }
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();
        boolean filtered = status != null
                || requesterType != null
                || q != null
                || fromAt != null
                || toAt != null;
        Page<SpaceEnquiryEntity> page = filtered
                ? enquiryRepository.searchForAdmin(status, requesterType, q, fromAt, toAt, safe)
                : enquiryRepository.findAllByOrderByRequestedAtDesc(safe);
        return PagedResponse.from(enrichAdminList(page));
    }

    @Transactional
    public AdminEnquirySummaryResponse summaryForAdmin() {
        expirePendingAndNotify(LocalDateTime.now(clock));
        return AdminEnquirySummaryResponse.builder()
                .totalEnquiries(enquiryRepository.count())
                .pendingCount(enquiryRepository.countByStatus(SpaceEnquiryStatus.PENDING))
                .sharedCount(enquiryRepository.countByStatus(SpaceEnquiryStatus.SHARED))
                .expiredCount(enquiryRepository.countByStatus(SpaceEnquiryStatus.EXPIRED))
                .rejectedCount(enquiryRepository.countByStatus(SpaceEnquiryStatus.REJECTED))
                .cancelledCount(enquiryRepository.countByStatus(SpaceEnquiryStatus.CANCELLED))
                .build();
    }

    @Transactional
    public AdminSpaceEnquiryDetailResponse getForAdmin(UUID enquiryId) {
        SpaceEnquiryEntity entity = load(enquiryId);
        expireIfDue(entity, LocalDateTime.now(clock));
        return toAdminDetail(entity);
    }

    @Transactional
    public void deleteForAdmin(UUID enquiryId) {
        SpaceEnquiryEntity entity = load(enquiryId);
        enquiryRepository.delete(entity);
    }

    private Page<AdminSpaceEnquiryListItemResponse> enrichAdminList(Page<SpaceEnquiryEntity> page) {
        Set<UUID> spaceIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (SpaceEnquiryEntity entity : page.getContent()) {
            spaceIds.add(entity.getSpaceId());
            userIds.add(entity.getRequesterUserId());
        }
        Map<UUID, SpaceEntity> spaces = spaceRepository.findAllById(spaceIds).stream()
                .collect(Collectors.toMap(SpaceEntity::getId, Function.identity()));
        Map<UUID, UserEntity> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        Set<UUID> testSpaceIds = testLeadSpaceIds(spaceIds);
        return page.map(entity -> {
            SpaceEntity space = spaces.get(entity.getSpaceId());
            UserEntity user = users.get(entity.getRequesterUserId());
            String address = space != null ? space.getAddress() : null;
            return AdminSpaceEnquiryListItemResponse.from(
                    entity,
                    space != null ? space.getType() : null,
                    address,
                    address,
                    user != null ? user.getMobileNumber() : null,
                    testSpaceIds.contains(entity.getSpaceId()));
        });
    }

    @Transactional
    public AdminSpaceEnquiryDetailResponse share(UUID enquiryId, UUID adminId) {
        SpaceEnquiryEntity entity = load(enquiryId);
        LocalDateTime now = LocalDateTime.now(clock);
        expireIfDue(entity, now);

        if (entity.getStatus() == SpaceEnquiryStatus.SHARED) {
            throw new BusinessException(
                    "ENQUIRY_ALREADY_SHARED",
                    "Contact details were already shared for this enquiry.",
                    HttpStatus.CONFLICT);
        }
        if (entity.getStatus() != SpaceEnquiryStatus.PENDING) {
            throw new BusinessException(
                    "ENQUIRY_NOT_PENDING",
                    "Only pending enquiries can be shared.",
                    HttpStatus.CONFLICT);
        }

        UserEntity requester = userRepository
                .findByIdAndIsActiveTrue(entity.getRequesterUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", entity.getRequesterUserId()));
        SpaceEntity space = spaceRepository
                .findByIdAndIsActiveTrue(entity.getSpaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", entity.getSpaceId()));

        if (isBlank(entity.getRequesterEmail())) {
            throw new BusinessException(
                    "REQUESTER_EMAIL_MISSING", "Requester email is required before sharing.", HttpStatus.CONFLICT);
        }

        OwnerContactResponse contact = ownerContactResolver.resolve(space);
        if (!ownerContactResolver.hasShareableContact(contact)) {
            throw new BusinessException(
                    "OWNER_CONTACT_UNAVAILABLE",
                    "Owner contact information is not available for this Space.",
                    HttpStatus.CONFLICT);
        }

        applyAuthorizedShare(entity, space, requester.getFullName(), contact, adminId, now);
        return AdminSpaceEnquiryDetailResponse.from(entity, space.getType(), space.getAddress(), contact);
    }

    @Transactional
    public AdminSpaceEnquiryDetailResponse reject(UUID enquiryId, UUID adminId, String reason) {
        SpaceEnquiryEntity entity = load(enquiryId);
        LocalDateTime now = LocalDateTime.now(clock);
        expireIfDue(entity, now);

        if (entity.getStatus() != SpaceEnquiryStatus.PENDING) {
            throw new BusinessException(
                    "ENQUIRY_NOT_PENDING",
                    "Only pending enquiries can be rejected.",
                    HttpStatus.CONFLICT);
        }

        entity.setStatus(SpaceEnquiryStatus.REJECTED);
        entity.setReviewedAt(now);
        entity.setRejectedAt(now);
        entity.setRejectionReason(blankToNull(reason));
        enquiryRepository.save(entity);
        notifyRequester(entity, NotificationType.CONTACT_ENQUIRY_REJECTED);
        enqueueRejectedEmail(entity);

        return toAdminDetail(entity);
    }

    @Transactional
    public AdminSpaceEnquiryDetailResponse expire(UUID enquiryId, UUID adminId) {
        SpaceEnquiryEntity entity = load(enquiryId);
        LocalDateTime now = LocalDateTime.now(clock);
        expireIfDue(entity, now);

        if (entity.getStatus() == SpaceEnquiryStatus.EXPIRED) {
            return toAdminDetail(entity);
        }
        if (entity.getStatus() != SpaceEnquiryStatus.PENDING
                && entity.getStatus() != SpaceEnquiryStatus.SHARED) {
            throw new BusinessException(
                    "ENQUIRY_NOT_ACTIVE",
                    "Only pending or shared enquiries can be expired.",
                    HttpStatus.CONFLICT);
        }

        SpaceEnquiryStatus previous = entity.getStatus();
        entity.setStatus(SpaceEnquiryStatus.EXPIRED);
        entity.setExpiresAt(now);
        entity.setReviewedAt(now);
        enquiryRepository.save(entity);
        notifyRequester(entity, NotificationType.CONTACT_ENQUIRY_EXPIRED);
        log.info(
                "Enquiry expired manually enquiryId={} spaceId={} previousStatus={} adminId={}",
                entity.getId(),
                entity.getSpaceId(),
                previous,
                adminId);

        return toAdminDetail(entity);
    }

    @Transactional
    public int expireDue() {
        return expirePendingAndNotify(LocalDateTime.now(clock));
    }

    private SpaceEnquiryResponse createNewEnquiry(
            UserEntity requester, SpaceEntity space, String email, LocalDateTime now) {
        try {
            return persistNewEnquiry(requester, space, email, now);
        } catch (DataIntegrityViolationException ex) {
            return recoverDuplicateCreate(space.getId(), requester.getId());
        }
    }

    private SpaceEnquiryResponse persistNewEnquiry(
            UserEntity requester, SpaceEntity space, String email, LocalDateTime now) {
        SpaceEnquiryEntity entity = SpaceEnquiryEntity.builder()
                .spaceId(space.getId())
                .spaceNameSnapshot(space.getName())
                .requesterUserId(requester.getId())
                .requesterNameSnapshot(requester.getFullName())
                .requesterEmail(email)
                .requesterType(resolveRequesterType(requester.getId()))
                .status(SpaceEnquiryStatus.PENDING)
                .requestedAt(now)
                .expiresAt(now.plusDays(retentionDays))
                .build();
        entity = enquiryRepository.saveAndFlush(entity);
        notifyRequester(entity, NotificationType.CONTACT_ENQUIRY_SUBMITTED);
        EnquiryAutoShareDecision decision = enquiryAutoSharePolicy.evaluate(space, entity.getRequesterEmail());
        if (decision.isAllowed()) {
            applyAuthorizedShare(entity, space, requester.getFullName(), decision.contact(), null, now);
            log.info(
                    "Enquiry auto-shared enquiryId={} spaceId={}",
                    entity.getId(),
                    space.getId());
        } else {
            log.info(
                    "Enquiry auto-share skipped enquiryId={} spaceId={} reason={}",
                    entity.getId(),
                    space.getId(),
                    decision.reason());
            notifyAdmins(entity, requester, space);
        }
        enqueueSupportSubmittedEmail(entity, requester, space, automaticallyShared(entity));
        return SpaceEnquiryResponse.from(entity);
    }

    private SpaceEnquiryResponse recoverDuplicateCreate(UUID spaceId, UUID requesterId) {
        return enquiryRepository
                .findBySpaceIdAndRequesterUserIdAndStatus(spaceId, requesterId, SpaceEnquiryStatus.PENDING)
                .or(() -> enquiryRepository.findBySpaceIdAndRequesterUserIdAndStatus(
                        spaceId, requesterId, SpaceEnquiryStatus.SHARED))
                .or(() -> enquiryRepository.findFirstBySpaceIdAndRequesterUserIdOrderByRequestedAtDesc(
                        spaceId, requesterId))
                .map(this::reuseExistingEnquiry)
                .orElseThrow(() -> new BusinessException(
                        "ENQUIRY_CREATE_CONFLICT",
                        "Could not create enquiry. Please try again.",
                        HttpStatus.CONFLICT));
    }

    private SpaceEnquiryResponse reuseExistingEnquiry(SpaceEnquiryEntity existing) {
        return reuseExistingEnquiry(existing, null);
    }

    private SpaceEnquiryResponse reuseExistingEnquiry(SpaceEnquiryEntity existing, SpaceEntity space) {
        boolean sentNewShareEmail = false;
        if (existing.getStatus() == SpaceEnquiryStatus.PENDING) {
            SpaceEntity listing = space != null
                    ? space
                    : spaceRepository
                            .findByIdAndIsActiveTrueAndDiscoverableTrue(existing.getSpaceId())
                            .orElse(null);
            if (listing != null) {
                EnquiryAutoShareDecision decision =
                        enquiryAutoSharePolicy.evaluate(listing, existing.getRequesterEmail());
                if (decision != null && decision.isAllowed()) {
                    UserEntity requester = userRepository
                            .findByIdAndIsActiveTrue(existing.getRequesterUserId())
                            .orElse(null);
                    String name = requester != null
                            ? requester.getFullName()
                            : existing.getRequesterNameSnapshot();
                    applyAuthorizedShare(
                            existing, listing, name, decision.contact(), null, LocalDateTime.now(clock));
                    sentNewShareEmail = true;
                }
            }
        }
        log.info(
                "Enquiry create reused enquiryId={} spaceId={} requesterUserId={} status={} sentNewShareEmail={}",
                existing.getId(),
                existing.getSpaceId(),
                existing.getRequesterUserId(),
                existing.getStatus(),
                sentNewShareEmail);
        SpaceEnquiryResponse response = SpaceEnquiryResponse.from(existing);
        if (sentNewShareEmail) {
            return response;
        }
        return response.toBuilder().reusedExisting(true).build();
    }

    private void applyAuthorizedShare(
            SpaceEnquiryEntity entity,
            SpaceEntity space,
            String requesterDisplayName,
            OwnerContactResponse contact,
            UUID adminId,
            LocalDateTime now) {
        EnquiryMailMessage mailMessage = new EnquiryMailMessage(
                entity.getRequesterEmail(),
                requesterDisplayName,
                space.getName(),
                space.getType(),
                space.getAddress(),
                contact,
                listingDetailsResolver.resolve(space));
        emailService.send(SendEmailCommand.builder()
                .eventType(EmailEventType.ENQUIRY_SHARED)
                .recipientUserId(entity.getRequesterUserId())
                .recipientEmail(entity.getRequesterEmail())
                .subject(EnquiryContactEmailComposer.subject(mailMessage))
                .plainBody(EnquiryContactEmailComposer.body(mailMessage))
                .htmlBody(EnquiryContactEmailComposer.htmlBody(mailMessage))
                .relatedEntityType(EmailService.RELATED_SPACE_ENQUIRY)
                .relatedEntityId(entity.getId())
                .idempotencyKey(EmailIdempotencyKeys.enquiryShared(entity.getId()))
                .build());

        entity.setStatus(SpaceEnquiryStatus.SHARED);
        entity.setReviewedAt(now);
        entity.setSharedAt(now);
        entity.setSharedByAdminId(adminId);
        enquiryRepository.save(entity);
        notifyRequester(entity, NotificationType.CONTACT_ENQUIRY_SHARED);
    }

    private void enqueueSupportSubmittedEmail(
            SpaceEnquiryEntity enquiry, UserEntity requester, SpaceEntity space, boolean automaticallyShared) {
        String supportTo = mailProperties.resolvedSupportAddress();
        if (isBlank(supportTo)) {
            log.warn("Enquiry {} created but acomi.mail.support-address is not configured", enquiry.getId());
            return;
        }
        String requesterEmail = enquiry.getRequesterEmail();
        String requesterName = enquiry.getRequesterNameSnapshot();
        String spaceName = enquiry.getSpaceNameSnapshot();
        emailService.send(SendEmailCommand.builder()
                .eventType(EmailEventType.ENQUIRY_SUBMITTED_SUPPORT)
                .recipientEmail(supportTo)
                .subject(EnquiryLifecycleEmailCopy.supportSubject(spaceName))
                .plainBody(EnquiryLifecycleEmailCopy.supportBody(
                        spaceName,
                        space != null ? space.getType() : null,
                        requesterName,
                        requesterEmail,
                        requester != null ? requester.getMobileNumber() : null,
                        enquiry.getId(),
                        enquiry.getRequestedAt(),
                        automaticallyShared))
                .relatedEntityType(EmailService.RELATED_SPACE_ENQUIRY)
                .relatedEntityId(enquiry.getId())
                .idempotencyKey(EmailIdempotencyKeys.enquirySubmittedSupport(enquiry.getId()))
                .build());
    }

    private void enqueueRejectedEmail(SpaceEnquiryEntity enquiry) {
        if (isBlank(enquiry.getRequesterEmail())) {
            return;
        }
        String spaceName = enquiry.getSpaceNameSnapshot();
        emailService.send(SendEmailCommand.builder()
                .eventType(EmailEventType.ENQUIRY_REJECTED)
                .recipientUserId(enquiry.getRequesterUserId())
                .recipientEmail(enquiry.getRequesterEmail())
                .subject(EnquiryLifecycleEmailCopy.rejectedSubject(spaceName))
                .plainBody(EnquiryLifecycleEmailCopy.rejectedBody(enquiry.getRequesterNameSnapshot(), spaceName))
                .relatedEntityType(EmailService.RELATED_SPACE_ENQUIRY)
                .relatedEntityId(enquiry.getId())
                .idempotencyKey(EmailIdempotencyKeys.enquiryRejected(enquiry.getId()))
                .build());
    }

    private void notifyAdmins(SpaceEnquiryEntity enquiry, UserEntity requester, SpaceEntity space) {
        List<UserEntity> admins = userRepository.findBySystemRoleAndIsActiveTrue(SystemRole.ADMIN);
        String title = "New contact enquiry";
        String message = requester.getFullName()
                + " has requested contact details for "
                + space.getName()
                + ".";
        for (UserEntity admin : admins) {
            notificationService.publish(PublishNotificationCommand.builder()
                    .spaceId(space.getId())
                    .userId(admin.getId())
                    .actorId(requester.getId())
                    .entityType(NotificationEntityType.SPACE_ENQUIRY)
                    .entityId(enquiry.getId())
                    .notificationType(NotificationType.CONTACT_ENQUIRY)
                    .category(NotificationCategory.ACTION_REQUIRED)
                    .priority(NotificationPriority.HIGH)
                    .title(title)
                    .message(message)
                    .actionLabel("View enquiry")
                    .actionRoute("AdminEnquiryDetail")
                    .dedupeKey("CONTACT_ENQUIRY:" + enquiry.getId() + ":" + admin.getId())
                    .build());
        }
        if (admins.isEmpty()) {
            log.warn("Enquiry {} created but no active admin users to notify", enquiry.getId());
        }
    }

    private void notifyRequester(SpaceEnquiryEntity enquiry, NotificationType type) {
        String spaceName = enquiry.getSpaceNameSnapshot() != null && !enquiry.getSpaceNameSnapshot().isBlank()
                ? enquiry.getSpaceNameSnapshot().trim()
                : "this listing";
        String title;
        String message;
        switch (type) {
            case CONTACT_ENQUIRY_SUBMITTED -> {
                title = "Enquiry submitted";
                message = "Your enquiry for " + spaceName + " has been submitted.";
            }
            case CONTACT_ENQUIRY_SHARED -> {
                title = "Contact details shared";
                message = "ACOMI has shared the contact details for "
                        + spaceName
                        + ". Check your email for the contact information.";
            }
            case CONTACT_ENQUIRY_REJECTED -> {
                title = "Unable to fulfil";
                message = "Your enquiry for " + spaceName + " was reviewed by ACOMI and could not be fulfilled.";
            }
            case CONTACT_ENQUIRY_EXPIRED -> {
                title = "Enquiry expired";
                message = "Your enquiry for " + spaceName + " has expired.";
            }
            default -> {
                return;
            }
        }
        notificationService.publish(PublishNotificationCommand.builder()
                .spaceId(enquiry.getSpaceId())
                .userId(enquiry.getRequesterUserId())
                .entityType(NotificationEntityType.SPACE_ENQUIRY)
                .entityId(enquiry.getId())
                .notificationType(type)
                .category(NotificationCategory.INFORMATION)
                .priority(NotificationPriority.MEDIUM)
                .title(title)
                .message(message)
                .actionLabel("View enquiry")
                .actionRoute("MyEnquiries")
                .dedupeKey(type.name() + ":" + enquiry.getId() + ":" + enquiry.getRequesterUserId())
                .build());
    }

    private int expirePendingAndNotify(LocalDateTime now) {
        List<SpaceEnquiryEntity> due =
                enquiryRepository.findByStatusAndExpiresAtLessThanEqual(SpaceEnquiryStatus.PENDING, now);
        int expired = enquiryRepository.expirePendingDue(now);
        for (SpaceEnquiryEntity enquiry : due) {
            enquiry.setStatus(SpaceEnquiryStatus.EXPIRED);
            notifyRequester(enquiry, NotificationType.CONTACT_ENQUIRY_EXPIRED);
        }
        return expired;
    }

    private EnquiryRequesterType resolveRequesterType(UUID userId) {
        if (spaceRepository.existsByOwnerIdAndIsActiveTrue(userId)) {
            return EnquiryRequesterType.OWNER;
        }
        return EnquiryRequesterType.MEMBER;
    }

    private String resolveRequesterEmail(UserEntity requester, CreateSpaceEnquiryRequest request) {
        String provided = request != null ? blankToNull(request.getEmail()) : null;
        String profile = blankToNull(requester.getEmail());
        String email = provided != null ? provided : profile;
        if (email == null) {
            throw new BusinessException(
                    "REQUESTER_EMAIL_REQUIRED",
                    "An email address is required so ACOMI can share owner contact details.",
                    HttpStatus.BAD_REQUEST);
        }
        return email.toLowerCase();
    }

    private SpaceEnquiryEntity load(UUID enquiryId) {
        return enquiryRepository
                .findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry", "id", enquiryId));
    }

    private AdminSpaceEnquiryDetailResponse toAdminDetail(SpaceEnquiryEntity entity) {
        SpaceEntity space = spaceRepository.findById(entity.getSpaceId()).orElse(null);
        OwnerContactResponse contact = ownerContactResolver.resolveOrEmpty(space);
        EnquiryListingDetails listing = space == null
                ? EnquiryListingDetails.empty()
                : listingDetailsResolver.resolve(space);
        if (listing == null) {
            listing = EnquiryListingDetails.empty();
        }
        String requesterMobile = userRepository
                .findById(entity.getRequesterUserId())
                .map(UserEntity::getMobileNumber)
                .orElse(null);
        return AdminSpaceEnquiryDetailResponse.from(
                entity,
                space != null ? space.getType() : null,
                space != null ? space.getAddress() : null,
                locationLabel(space, listing),
                listing.capacity(),
                requesterMobile,
                contact,
                isTestLeadSpace(entity.getSpaceId()));
    }

    private Set<UUID> testLeadSpaceIds(Set<UUID> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> testIds = new HashSet<>();
        testIds.addAll(propertyRegistrationRepository.findTestLeadConvertedSpaceIds(spaceIds));
        testIds.addAll(messRegistrationRepository.findTestLeadConvertedSpaceIds(spaceIds));
        return testIds;
    }

    private boolean isTestLeadSpace(UUID spaceId) {
        if (spaceId == null) {
            return false;
        }
        return !testLeadSpaceIds(Set.of(spaceId)).isEmpty();
    }

    private void expireIfDue(SpaceEnquiryEntity entity, LocalDateTime now) {
        if (entity == null) {
            return;
        }
        if (entity.getStatus() == SpaceEnquiryStatus.PENDING
                && entity.getExpiresAt() != null
                && !entity.getExpiresAt().isAfter(now)) {
            entity.setStatus(SpaceEnquiryStatus.EXPIRED);
            enquiryRepository.save(entity);
            notifyRequester(entity, NotificationType.CONTACT_ENQUIRY_EXPIRED);
        }
    }

    private Map<UUID, SpaceEntity> loadSpaces(List<SpaceEnquiryEntity> rows) {
        List<UUID> ids = rows.stream().map(SpaceEnquiryEntity::getSpaceId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SpaceEntity> spaces = new HashMap<>();
        for (SpaceEntity space : spaceRepository.findAllById(ids)) {
            spaces.put(space.getId(), space);
        }
        return spaces;
    }

    private SpaceEnquiryResponse toMemberResponse(
            SpaceEnquiryEntity entity, SpaceEntity space, EnquiryListingDetails listing) {
        EnquiryListingDetails safe = listing == null ? EnquiryListingDetails.empty() : listing;
        return SpaceEnquiryResponse.from(
                entity,
                space != null ? space.getType() : null,
                locationLabel(space, safe),
                safe.sharingNotes(),
                amenityLabels(safe.amenities()),
                space != null ? space.isFoodIncludedInRent() : null);
    }

    private static String locationLabel(SpaceEntity space, EnquiryListingDetails listing) {
        EnquiryListingDetails safe = listing == null ? EnquiryListingDetails.empty() : listing;
        String city = safe.city();
        String addressLine = safe.addressLine();
        if (city != null && addressLine != null && !addressLine.equalsIgnoreCase(city)) {
            int comma = addressLine.indexOf(',');
            String locality = (comma > 0 ? addressLine.substring(0, comma) : addressLine).trim();
            if (!locality.isEmpty() && !locality.equalsIgnoreCase(city)) {
                return locality + ", " + city;
            }
            return city;
        }
        if (city != null) {
            return city;
        }
        String location = safe.location();
        if (location != null) {
            return firstTwoParts(location);
        }
        if (space == null || space.getAddress() == null || space.getAddress().isBlank()) {
            return null;
        }
        return firstTwoParts(space.getAddress().trim());
    }

    private static String firstTwoParts(String value) {
        String[] parts = value.split(",");
        if (parts.length >= 2) {
            return parts[0].trim() + ", " + parts[1].trim();
        }
        return value;
    }

    private static List<String> amenityLabels(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(","))
                .map(String::trim)
                .filter(label -> !label.isEmpty())
                .toList();
    }

    private static Pageable safePage(Pageable pageable) {
        int page = pageable != null ? Math.max(pageable.getPageNumber(), 0) : 0;
        int size = pageable != null ? pageable.getPageSize() : 20;
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(page, safeSize);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean automaticallyShared(SpaceEnquiryEntity entity) {
        return entity.getStatus() == SpaceEnquiryStatus.SHARED && entity.getSharedByAdminId() == null;
    }
}
