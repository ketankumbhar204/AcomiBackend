package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateListingMarketingRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingActivityResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminListingMarketingResponse;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingChannel;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingKind;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingOutcome;
import com.acomi.acomi_backend.admin.domain.model.ListingMarketingStatus;
import com.acomi.acomi_backend.admin.domain.model.WhatsAppAvailability;
import com.acomi.acomi_backend.admin.infrastructure.persistence.entity.AdminListingMarketingActivityEntity;
import com.acomi.acomi_backend.admin.infrastructure.persistence.repository.AdminListingMarketingActivityRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manual listing marketing for admin WhatsApp/call workflows.
 *
 * <p>Persistence strategy: marketing status lives on registrations (survives convert/link via
 * {@code converted_space_id}) and on spaces for native listings with no registration. Updates to an
 * active space that has a linked registration write the registration row; activities keep both IDs
 * when known so history remains queryable after conversion.
 */
@Service
@RequiredArgsConstructor
public class AdminListingMarketingService {

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final SpaceRepository spaceRepository;
    private final AdminListingMarketingActivityRepository activityRepository;

    @Transactional(readOnly = true)
    public AdminListingMarketingResponse getPropertyRegistrationMarketing(UUID registrationId) {
        PropertyRegistrationEntity entity = requireProperty(registrationId);
        return buildResponse(
                entity.getMarketingStatus(),
                entity.getWhatsappAvailability(),
                entity.getLastSharedAt(),
                entity.getLastSharedBy(),
                entity.getMarketingNotes(),
                entity.getId(),
                null,
                entity.getConvertedSpaceId(),
                loadPropertyActivities(entity));
    }

    @Transactional(readOnly = true)
    public AdminListingMarketingResponse getMessRegistrationMarketing(UUID registrationId) {
        MessRegistrationEntity entity = requireMess(registrationId);
        return buildResponse(
                entity.getMarketingStatus(),
                entity.getWhatsappAvailability(),
                entity.getLastSharedAt(),
                entity.getLastSharedBy(),
                entity.getMarketingNotes(),
                null,
                entity.getId(),
                entity.getConvertedSpaceId(),
                loadMessActivities(entity));
    }

    @Transactional(readOnly = true)
    public AdminListingMarketingResponse getSpaceMarketing(UUID spaceId) {
        SpaceEntity space = requireSpace(spaceId);
        Optional<PropertyRegistrationEntity> property =
                propertyRegistrationRepository.findByConvertedSpaceId(spaceId);
        if (property.isPresent()) {
            return getPropertyRegistrationMarketing(property.get().getId());
        }
        Optional<MessRegistrationEntity> mess = messRegistrationRepository.findByConvertedSpaceId(spaceId);
        if (mess.isPresent()) {
            return getMessRegistrationMarketing(mess.get().getId());
        }
        return buildResponse(
                defaultStatus(space.getMarketingStatus()),
                defaultAvailability(space.getWhatsappAvailability()),
                space.getLastSharedAt(),
                space.getLastSharedBy(),
                space.getMarketingNotes(),
                null,
                null,
                space.getId(),
                activityRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream()
                        .map(this::toActivityResponse)
                        .toList());
    }

    @Transactional
    public AdminListingMarketingResponse updatePropertyRegistrationMarketing(
            UUID registrationId, AdminUpdateListingMarketingRequest request) {
        PropertyRegistrationEntity entity = requireProperty(registrationId);
        UUID actorId = SecurityUtils.getCurrentUserId();
        applyStatusUpdates(
                request,
                entity.getMarketingStatus(),
                entity.getWhatsappAvailability(),
                (status, availability, notes, sharedAt, sharedBy) -> {
                    if (status != null) {
                        entity.setMarketingStatus(status);
                    }
                    if (availability != null) {
                        entity.setWhatsappAvailability(availability);
                    }
                    if (notes != null) {
                        entity.setMarketingNotes(notes);
                    }
                    if (sharedAt != null) {
                        entity.setLastSharedAt(sharedAt);
                        entity.setLastSharedBy(sharedBy);
                    }
                },
                actorId);
        propertyRegistrationRepository.save(entity);
        syncConvertedSpace(entity.getConvertedSpaceId(), entity.getMarketingStatus(), entity.getWhatsappAvailability(),
                entity.getLastSharedAt(), entity.getLastSharedBy(), entity.getMarketingNotes());
        recordActivity(
                request,
                ListingMarketingKind.PROPERTY,
                entity.getId(),
                null,
                entity.getConvertedSpaceId(),
                actorId);
        return getPropertyRegistrationMarketing(registrationId);
    }

    @Transactional
    public AdminListingMarketingResponse updateMessRegistrationMarketing(
            UUID registrationId, AdminUpdateListingMarketingRequest request) {
        MessRegistrationEntity entity = requireMess(registrationId);
        UUID actorId = SecurityUtils.getCurrentUserId();
        applyStatusUpdates(
                request,
                entity.getMarketingStatus(),
                entity.getWhatsappAvailability(),
                (status, availability, notes, sharedAt, sharedBy) -> {
                    if (status != null) {
                        entity.setMarketingStatus(status);
                    }
                    if (availability != null) {
                        entity.setWhatsappAvailability(availability);
                    }
                    if (notes != null) {
                        entity.setMarketingNotes(notes);
                    }
                    if (sharedAt != null) {
                        entity.setLastSharedAt(sharedAt);
                        entity.setLastSharedBy(sharedBy);
                    }
                },
                actorId);
        messRegistrationRepository.save(entity);
        syncConvertedSpace(entity.getConvertedSpaceId(), entity.getMarketingStatus(), entity.getWhatsappAvailability(),
                entity.getLastSharedAt(), entity.getLastSharedBy(), entity.getMarketingNotes());
        recordActivity(
                request,
                ListingMarketingKind.MESS,
                null,
                entity.getId(),
                entity.getConvertedSpaceId(),
                actorId);
        return getMessRegistrationMarketing(registrationId);
    }

    @Transactional
    public AdminListingMarketingResponse updateSpaceMarketing(
            UUID spaceId, AdminUpdateListingMarketingRequest request) {
        SpaceEntity space = requireSpace(spaceId);
        Optional<PropertyRegistrationEntity> property =
                propertyRegistrationRepository.findByConvertedSpaceId(spaceId);
        if (property.isPresent()) {
            return updatePropertyRegistrationMarketing(property.get().getId(), request);
        }
        Optional<MessRegistrationEntity> mess = messRegistrationRepository.findByConvertedSpaceId(spaceId);
        if (mess.isPresent()) {
            return updateMessRegistrationMarketing(mess.get().getId(), request);
        }

        UUID actorId = SecurityUtils.getCurrentUserId();
        applyStatusUpdates(
                request,
                space.getMarketingStatus(),
                space.getWhatsappAvailability(),
                (status, availability, notes, sharedAt, sharedBy) -> {
                    if (status != null) {
                        space.setMarketingStatus(status);
                    }
                    if (availability != null) {
                        space.setWhatsappAvailability(availability);
                    }
                    if (notes != null) {
                        space.setMarketingNotes(notes);
                    }
                    if (sharedAt != null) {
                        space.setLastSharedAt(sharedAt);
                        space.setLastSharedBy(sharedBy);
                    }
                },
                actorId);
        spaceRepository.save(space);
        ListingMarketingKind kind =
                space.getType() == SpaceType.MESS ? ListingMarketingKind.MESS : ListingMarketingKind.PROPERTY;
        recordActivity(request, kind, null, null, space.getId(), actorId);
        return getSpaceMarketing(spaceId);
    }

    private void syncConvertedSpace(
            UUID spaceId,
            ListingMarketingStatus status,
            WhatsAppAvailability availability,
            LocalDateTime lastSharedAt,
            UUID lastSharedBy,
            String notes) {
        if (spaceId == null) {
            return;
        }
        spaceRepository.findById(spaceId).ifPresent(space -> {
            space.setMarketingStatus(defaultStatus(status));
            space.setWhatsappAvailability(defaultAvailability(availability));
            space.setLastSharedAt(lastSharedAt);
            space.setLastSharedBy(lastSharedBy);
            space.setMarketingNotes(notes);
            spaceRepository.save(space);
        });
    }

    private void applyStatusUpdates(
            AdminUpdateListingMarketingRequest request,
            ListingMarketingStatus currentStatus,
            WhatsAppAvailability currentAvailability,
            MarketingMutator mutator,
            UUID actorId) {
        ListingMarketingStatus nextStatus = request.getMarketingStatus();
        WhatsAppAvailability nextAvailability = request.getWhatsappAvailability();
        String notes = request.getMarketingNotes();

        AdminUpdateListingMarketingRequest.Activity activity = request.getActivity();
        if (activity != null && activity.getOutcome() != null) {
            switch (activity.getOutcome()) {
                case SHARED_CONFIRMED -> nextStatus = ListingMarketingStatus.SHARED;
                case WHATSAPP_UNAVAILABLE -> {
                    if (nextAvailability == null) {
                        nextAvailability = WhatsAppAvailability.UNAVAILABLE;
                    }
                }
                case WHATSAPP_OPENED,
                        CALL_ATTEMPTED,
                        CONTACTED_BY_PHONE,
                        SKIPPED,
                        WHATSAPP_API_SUBMITTED,
                        WHATSAPP_API_FAILED,
                        WHATSAPP_API_BLOCKED -> {
                    // Opening WhatsApp / API submit must never auto-mark SHARED.
                }
            }
        }

        LocalDateTime sharedAt = null;
        UUID sharedBy = null;
        ListingMarketingStatus effectiveStatus = nextStatus != null ? nextStatus : currentStatus;
        if (nextStatus == ListingMarketingStatus.SHARED
                || (activity != null && activity.getOutcome() == ListingMarketingOutcome.SHARED_CONFIRMED)) {
            sharedAt = LocalDateTime.now();
            sharedBy = actorId;
            effectiveStatus = ListingMarketingStatus.SHARED;
            nextStatus = ListingMarketingStatus.SHARED;
        }

        if (nextStatus == null && nextAvailability == null && notes == null && activity == null) {
            throw new BusinessException("No marketing updates provided", HttpStatus.BAD_REQUEST);
        }

        mutator.apply(
                nextStatus,
                nextAvailability,
                notes,
                sharedAt,
                sharedBy);

        // Keep compiler happy for unused effective vars when only notes/activity recorded.
        if (effectiveStatus == null && currentAvailability == null) {
            return;
        }
    }

    private void recordActivity(
            AdminUpdateListingMarketingRequest request,
            ListingMarketingKind kind,
            UUID propertyRegistrationId,
            UUID messRegistrationId,
            UUID spaceId,
            UUID actorId) {
        AdminUpdateListingMarketingRequest.Activity activity = request.getActivity();
        if (activity == null || activity.getOutcome() == null) {
            return;
        }
        ListingMarketingChannel channel = activity.getChannel();
        if (channel == null) {
            channel = activity.getOutcome().name().startsWith("CALL") || activity.getOutcome() == ListingMarketingOutcome.CONTACTED_BY_PHONE
                    ? ListingMarketingChannel.CALL
                    : ListingMarketingChannel.WHATSAPP;
        }
        activityRepository.save(AdminListingMarketingActivityEntity.builder()
                .listingKind(kind)
                .propertyRegistrationId(propertyRegistrationId)
                .messRegistrationId(messRegistrationId)
                .spaceId(spaceId)
                .channel(channel)
                .outcome(activity.getOutcome())
                .templateKey(trimToNull(activity.getTemplateKey()))
                .note(trimToNull(activity.getNote()))
                .messageSnapshot(trimToNull(activity.getMessageSnapshot()))
                .actorUserId(actorId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private List<AdminListingMarketingActivityResponse> loadPropertyActivities(
            PropertyRegistrationEntity entity) {
        List<AdminListingMarketingActivityEntity> activities = new ArrayList<>(
                activityRepository.findByPropertyRegistrationIdOrderByCreatedAtDesc(entity.getId()));
        if (entity.getConvertedSpaceId() != null) {
            for (AdminListingMarketingActivityEntity activity :
                    activityRepository.findBySpaceIdOrderByCreatedAtDesc(entity.getConvertedSpaceId())) {
                if (activity.getPropertyRegistrationId() == null) {
                    activities.add(activity);
                }
            }
        }
        return activities.stream()
                .sorted(Comparator.comparing(AdminListingMarketingActivityEntity::getCreatedAt).reversed())
                .map(this::toActivityResponse)
                .toList();
    }

    private List<AdminListingMarketingActivityResponse> loadMessActivities(MessRegistrationEntity entity) {
        List<AdminListingMarketingActivityEntity> activities = new ArrayList<>(
                activityRepository.findByMessRegistrationIdOrderByCreatedAtDesc(entity.getId()));
        if (entity.getConvertedSpaceId() != null) {
            for (AdminListingMarketingActivityEntity activity :
                    activityRepository.findBySpaceIdOrderByCreatedAtDesc(entity.getConvertedSpaceId())) {
                if (activity.getMessRegistrationId() == null) {
                    activities.add(activity);
                }
            }
        }
        return activities.stream()
                .sorted(Comparator.comparing(AdminListingMarketingActivityEntity::getCreatedAt).reversed())
                .map(this::toActivityResponse)
                .toList();
    }

    private AdminListingMarketingResponse buildResponse(
            ListingMarketingStatus status,
            WhatsAppAvailability availability,
            LocalDateTime lastSharedAt,
            UUID lastSharedBy,
            String notes,
            UUID propertyRegistrationId,
            UUID messRegistrationId,
            UUID spaceId,
            List<AdminListingMarketingActivityResponse> activities) {
        return AdminListingMarketingResponse.builder()
                .marketingStatus(defaultStatus(status))
                .whatsappAvailability(defaultAvailability(availability))
                .lastSharedAt(lastSharedAt)
                .lastSharedBy(lastSharedBy)
                .marketingNotes(notes)
                .propertyRegistrationId(propertyRegistrationId)
                .messRegistrationId(messRegistrationId)
                .spaceId(spaceId)
                .activities(activities == null ? List.of() : activities)
                .build();
    }

    private AdminListingMarketingActivityResponse toActivityResponse(
            AdminListingMarketingActivityEntity entity) {
        return AdminListingMarketingActivityResponse.builder()
                .id(entity.getId())
                .listingKind(entity.getListingKind())
                .propertyRegistrationId(entity.getPropertyRegistrationId())
                .messRegistrationId(entity.getMessRegistrationId())
                .spaceId(entity.getSpaceId())
                .channel(entity.getChannel())
                .outcome(entity.getOutcome())
                .templateKey(entity.getTemplateKey())
                .note(entity.getNote())
                .deliveryMode(entity.getDeliveryMode())
                .providerMessageId(entity.getProviderMessageId())
                .recipientPhoneMasked(entity.getRecipientPhoneMasked())
                .actorUserId(entity.getActorUserId())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private PropertyRegistrationEntity requireProperty(UUID id) {
        return propertyRegistrationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PropertyRegistration", "id", id));
    }

    private MessRegistrationEntity requireMess(UUID id) {
        return messRegistrationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MessRegistration", "id", id));
    }

    private SpaceEntity requireSpace(UUID id) {
        return spaceRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", id));
    }

    private static ListingMarketingStatus defaultStatus(ListingMarketingStatus status) {
        return status == null ? ListingMarketingStatus.NOT_SHARED : status;
    }

    private static WhatsAppAvailability defaultAvailability(WhatsAppAvailability availability) {
        return availability == null ? WhatsAppAvailability.UNKNOWN : availability;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @FunctionalInterface
    private interface MarketingMutator {
        void apply(
                ListingMarketingStatus status,
                WhatsAppAvailability availability,
                String notes,
                LocalDateTime sharedAt,
                UUID sharedBy);
    }
}
