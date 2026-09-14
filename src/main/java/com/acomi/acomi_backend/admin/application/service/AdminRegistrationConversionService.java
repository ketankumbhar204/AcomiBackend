package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.request.AdminLinkOwnerRequest;
import com.acomi.acomi_backend.admin.api.dto.request.AdminUpdateRegistrationReviewRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegistrationConvertResponse;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoShareDecision;
import com.acomi.acomi_backend.enquiry.domain.policy.EnquiryAutoSharePolicy;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.security.SecurityUtils;
import com.acomi.acomi_backend.mess.application.mapper.MessRegistrationMapper;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationDetailResponse;
import com.acomi.acomi_backend.mess.application.service.MessRegistrationService;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationDetailResponse;
import com.acomi.acomi_backend.property.application.mapper.PropertyRegistrationMapper;
import com.acomi.acomi_backend.property.application.service.PropertyRegistrationService;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.registration.application.RegistrationMobiles;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.api.dto.response.SpaceResponse;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Orchestrates admin lead → live Space conversion. Always creates spaces via
 * {@link SpaceService#createSpace} (no second INSERT path).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRegistrationConversionService {

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final MessRegistrationRepository messRegistrationRepository;
    private final PropertyRegistrationService propertyRegistrationService;
    private final MessRegistrationService messRegistrationService;
    private final UserRepository userRepository;
    private final SpaceService spaceService;
    private final SpaceRepository spaceRepository;
    private final EnquiryAutoSharePolicy enquiryAutoSharePolicy;

    @Transactional
    public PropertyRegistrationDetailResponse linkPropertyOwner(UUID registrationId, AdminLinkOwnerRequest request) {
        propertyRegistrationService.requireEntity(registrationId);
        PropertyRegistrationEntity entity = propertyRegistrationRepository
                .lockById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Property registration", "id", registrationId));
        UserEntity owner = requireLinkableOwner(request.getUserId());
        applyOwnerLink(entity.getLinkedOwnerUserId(), entity.getConvertedSpaceId(), owner);
        entity.setLinkedOwnerUserId(owner.getId());
        log.info(
                "Admin linked property owner registrationId={} spaceId={} ownerUserId={}",
                entity.getId(),
                entity.getConvertedSpaceId(),
                owner.getId());
        return enrichPropertyDetail(propertyRegistrationRepository.save(entity));
    }

    @Transactional
    public MessRegistrationDetailResponse linkMessOwner(UUID registrationId, AdminLinkOwnerRequest request) {
        messRegistrationService.requireEntity(registrationId);
        MessRegistrationEntity entity = messRegistrationRepository
                .lockById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Mess registration", "id", registrationId));
        UserEntity owner = requireLinkableOwner(request.getUserId());
        applyOwnerLink(entity.getLinkedOwnerUserId(), entity.getConvertedSpaceId(), owner);
        entity.setLinkedOwnerUserId(owner.getId());
        log.info(
                "Admin linked mess owner registrationId={} spaceId={} ownerUserId={}",
                entity.getId(),
                entity.getConvertedSpaceId(),
                owner.getId());
        return enrichMessDetail(messRegistrationRepository.save(entity));
    }

    public PropertyRegistrationDetailResponse enrichPropertyDetail(PropertyRegistrationEntity entity) {
        UserEntity linked = loadLinkedUser(entity.getLinkedOwnerUserId());
        SpaceEntity space = loadConvertedSpace(entity.getConvertedSpaceId());
        EnquiryAutoShareDecision listingDecision = listingDecision(space);
        return PropertyRegistrationMapper.toDetail(entity).toBuilder()
                .linkedOwnerName(linked != null ? linked.getFullName() : null)
                .linkedOwnerMobile(linked != null ? linked.getMobileNumber() : null)
                .ownershipStatus(entity.getLinkedOwnerUserId() != null ? "LINKED" : "NOT_LINKED")
                .autoShareEligible(listingDecision != null && listingDecision.isAllowed())
                .autoShareReason(
                        listingDecision != null && listingDecision.reason() != null
                                ? listingDecision.reason().name()
                                : null)
                .build();
    }

    public MessRegistrationDetailResponse enrichMessDetail(MessRegistrationEntity entity) {
        UserEntity linked = loadLinkedUser(entity.getLinkedOwnerUserId());
        SpaceEntity space = loadConvertedSpace(entity.getConvertedSpaceId());
        EnquiryAutoShareDecision listingDecision = listingDecision(space);
        return MessRegistrationMapper.toDetail(entity).toBuilder()
                .linkedOwnerName(linked != null ? linked.getFullName() : null)
                .linkedOwnerMobile(linked != null ? linked.getMobileNumber() : null)
                .ownershipStatus(entity.getLinkedOwnerUserId() != null ? "LINKED" : "NOT_LINKED")
                .autoShareEligible(listingDecision != null && listingDecision.isAllowed())
                .autoShareReason(
                        listingDecision != null && listingDecision.reason() != null
                                ? listingDecision.reason().name()
                                : null)
                .build();
    }

    @Transactional
    public PropertyRegistrationDetailResponse updatePropertyReview(
            UUID registrationId, AdminUpdateRegistrationReviewRequest request) {
        PropertyRegistrationEntity entity = propertyRegistrationService.requireEntity(registrationId);
        assertNotConverted(entity.getStatus() == PropertyRegistrationStatus.CONVERTED, entity.getConvertedSpaceId());
        applySharedReview(entity, request);
        if (StringUtils.hasText(request.getPropertyOrMessName())) {
            entity.setPropertyName(request.getPropertyOrMessName().trim());
        }
        if (request.getStartingOrMonthlyPrice() != null) {
            entity.setStartingPrice(request.getStartingOrMonthlyPrice());
        }
        if (request.getAmenities() != null && SpaceAmenityService.supportsAmenities(entity.getPropertyType())) {
            entity.getAmenities().clear();
            int order = 0;
            for (AmenityAssignmentDto amenity : SpaceAmenityService.normalizeAssignments(request.getAmenities())) {
                boolean custom = AmenityCode.CUSTOM.name().equals(amenity.getCode());
                entity.addAmenity(PropertyRegistrationAmenityEntity.builder()
                        .amenityCode(amenity.getCode())
                        .customLabel(custom ? amenity.getLabel() : null)
                        .displayOrder(order++)
                        .build());
            }
        }
        if (StringUtils.hasText(request.getReviewNotes())) {
            entity.setReviewNotes(request.getReviewNotes().trim());
        }
        return enrichPropertyDetail(propertyRegistrationRepository.save(entity));
    }

    @Transactional
    public MessRegistrationDetailResponse updateMessReview(
            UUID registrationId, AdminUpdateRegistrationReviewRequest request) {
        MessRegistrationEntity entity = messRegistrationService.requireEntity(registrationId);
        assertNotConverted(entity.getStatus() == MessRegistrationStatus.CONVERTED, entity.getConvertedSpaceId());
        applySharedReview(entity, request);
        if (StringUtils.hasText(request.getPropertyOrMessName())) {
            entity.setMessName(request.getPropertyOrMessName().trim());
        }
        if (request.getStartingOrMonthlyPrice() != null) {
            entity.setMonthlyPrice(request.getStartingOrMonthlyPrice());
        }
        if (request.getMealPrice() != null) {
            entity.setMealPrice(request.getMealPrice());
        }
        if (StringUtils.hasText(request.getReviewNotes())) {
            entity.setReviewNotes(request.getReviewNotes().trim());
        }
        return enrichMessDetail(messRegistrationRepository.save(entity));
    }

    @Transactional
    public AdminRegistrationConvertResponse convertProperty(UUID registrationId) {
        return convertProperty(registrationId, SecurityUtils.getCurrentUserId());
    }

    /**
     * Admin-verified publish: creates a live Space via {@link SpaceService#createSpace}.
     * No ACOMI user association is required. Owner name is optional. Explicitly linked
     * owners are used when present; otherwise the acting admin is a provisional Space
     * owner so listings go live/discoverable. Real owners can claim/replace later.
     * Non-test Spaces are discoverable immediately.
     */
    @Transactional
    public AdminRegistrationConvertResponse convertProperty(UUID registrationId, UUID fallbackOwnerUserId) {
        PropertyRegistrationEntity entity = propertyRegistrationService.requireEntity(registrationId);
        if (entity.getStatus() == PropertyRegistrationStatus.CONVERTED || entity.getConvertedSpaceId() != null) {
            throw new BusinessException("Registration is already converted", HttpStatus.CONFLICT);
        }
        UserEntity owner = resolveOwnerForAdminPublish(entity.getLinkedOwnerUserId(), fallbackOwnerUserId);

        boolean discoverable = !entity.isTestLead();
        CreateSpaceRequest createRequest = new CreateSpaceRequest();
        createRequest.setName(entity.getPropertyName());
        createRequest.setType(entity.getPropertyType());
        createRequest.setAddress(flattenAddress(entity.getAddressLine(), entity.getCity(), entity.getState(), entity.getPincode()));
        createRequest.setContactNumber(usableListingMobile(
                entity.getMobileNumber(),
                entity.getAlternateMobileNumber(),
                entity.getAdditionalMobileNumber(),
                owner));
        createRequest.setOwnerId(owner.getId());
        createRequest.setDiscoverable(discoverable);
        createRequest.setGenderPolicy(entity.getGenderPolicy());
        createRequest.setFoodIncludedInRent(Boolean.TRUE.equals(entity.getFoodIncludedListing()));
        createRequest.setLatitude(entity.getLatitude());
        createRequest.setLongitude(entity.getLongitude());
        createRequest.setAmenities(toAmenityAssignments(entity));

        SpaceResponse space = spaceService.createSpace(createRequest);

        entity.setConvertedSpaceId(space.getId());
        entity.setStatus(PropertyRegistrationStatus.CONVERTED);
        propertyRegistrationRepository.save(entity);

        log.info(
                "Converted property registration {} to space {} (discoverable={}, provisionalOwner={})",
                entity.getReference(),
                space.getId(),
                discoverable,
                entity.getLinkedOwnerUserId() == null);

        return AdminRegistrationConvertResponse.builder()
                .registrationId(entity.getId())
                .reference(entity.getReference())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .discoverable(discoverable)
                .build();
    }

    @Transactional
    public AdminRegistrationConvertResponse convertMess(UUID registrationId) {
        return convertMess(registrationId, SecurityUtils.getCurrentUserId());
    }

    @Transactional
    public AdminRegistrationConvertResponse convertMess(UUID registrationId, UUID fallbackOwnerUserId) {
        MessRegistrationEntity entity = messRegistrationService.requireEntity(registrationId);
        if (entity.getStatus() == MessRegistrationStatus.CONVERTED || entity.getConvertedSpaceId() != null) {
            throw new BusinessException("Registration is already converted", HttpStatus.CONFLICT);
        }
        UserEntity owner = resolveOwnerForAdminPublish(entity.getLinkedOwnerUserId(), fallbackOwnerUserId);

        boolean discoverable = !entity.isTestLead();
        CreateSpaceRequest createRequest = new CreateSpaceRequest();
        createRequest.setName(entity.getMessName());
        createRequest.setType(SpaceType.MESS);
        createRequest.setAddress(flattenAddress(entity.getAddressLine(), entity.getCity(), entity.getState(), entity.getPincode()));
        createRequest.setContactNumber(usableListingMobile(
                entity.getMobileNumber(),
                entity.getAlternateMobileNumber(),
                entity.getAdditionalMobileNumber(),
                owner));
        createRequest.setOwnerId(owner.getId());
        createRequest.setDiscoverable(discoverable);
        createRequest.setGenderPolicy(entity.getGenderPolicy());
        createRequest.setFoodIncludedInRent(Boolean.TRUE.equals(entity.getFoodIncludedListing()));
        createRequest.setLatitude(entity.getLatitude());
        createRequest.setLongitude(entity.getLongitude());

        SpaceResponse space = spaceService.createSpace(createRequest);

        entity.setConvertedSpaceId(space.getId());
        entity.setStatus(MessRegistrationStatus.CONVERTED);
        messRegistrationRepository.save(entity);

        log.info(
                "Converted mess registration {} to space {} (discoverable={}, provisionalOwner={})",
                entity.getReference(),
                space.getId(),
                discoverable,
                entity.getLinkedOwnerUserId() == null);

        return AdminRegistrationConvertResponse.builder()
                .registrationId(entity.getId())
                .reference(entity.getReference())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .discoverable(discoverable)
                .build();
    }

    /** Publish every open admin property lead to a discoverable live Space. */
    @Transactional
    public int publishOpenAdminPropertyLeads() {
        UUID fallbackOwnerId = SecurityUtils.getCurrentUserId();
        int published = 0;
        for (PropertyRegistrationEntity entity : propertyRegistrationRepository.findOpenAdminLeads()) {
            try {
                convertProperty(entity.getId(), fallbackOwnerId);
                published++;
            } catch (Exception ex) {
                log.warn("Could not publish admin property lead {}: {}", entity.getReference(), ex.getMessage());
            }
        }
        return published;
    }

    /** Publish every open admin mess lead to a discoverable live Space. */
    @Transactional
    public int publishOpenAdminMessLeads() {
        UUID fallbackOwnerId = SecurityUtils.getCurrentUserId();
        int published = 0;
        for (MessRegistrationEntity entity : messRegistrationRepository.findOpenAdminLeads()) {
            try {
                convertMess(entity.getId(), fallbackOwnerId);
                published++;
            } catch (Exception ex) {
                log.warn("Could not publish admin mess lead {}: {}", entity.getReference(), ex.getMessage());
            }
        }
        return published;
    }

    @Transactional
    public SpaceEntity enableDiscovery(UUID spaceId) {
        SpaceEntity space = spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
        if (!space.isActive()) {
            throw new BusinessException("Inactive spaces cannot be made discoverable", HttpStatus.BAD_REQUEST);
        }
        if (isConvertedFromTestLead(spaceId)) {
            throw new BusinessException(
                    "Spaces converted from test leads cannot become discoverable", HttpStatus.BAD_REQUEST);
        }
        space.setDiscoverable(true);
        return spaceRepository.save(space);
    }

    private boolean isConvertedFromTestLead(UUID spaceId) {
        return propertyRegistrationRepository.findByConvertedSpaceId(spaceId)
                        .map(PropertyRegistrationEntity::isTestLead)
                        .orElse(false)
                || messRegistrationRepository.findByConvertedSpaceId(spaceId)
                        .map(MessRegistrationEntity::isTestLead)
                        .orElse(false);
    }

    private void applySharedReview(Object entityObj, AdminUpdateRegistrationReviewRequest request) {
        if (entityObj instanceof PropertyRegistrationEntity entity) {
            if (StringUtils.hasText(request.getOwnerName())) {
                entity.setOwnerName(request.getOwnerName().trim());
            }
            if (StringUtils.hasText(request.getMobileNumber())) {
                entity.setMobileNumber(request.getMobileNumber().trim());
            }
            if (request.getAlternateMobileNumber() != null || request.getMobileNumber() != null) {
                entity.setAlternateMobileNumber(RegistrationMobiles.resolveAlternate(
                        entity.getMobileNumber(),
                        request.getAlternateMobileNumber() != null
                                ? request.getAlternateMobileNumber()
                                : entity.getAlternateMobileNumber()));
            }
            if (request.getAdditionalMobileNumber() != null
                    || request.getAlternateMobileNumber() != null
                    || request.getMobileNumber() != null) {
                entity.setAdditionalMobileNumber(RegistrationMobiles.resolveAdditional(
                        entity.getMobileNumber(),
                        entity.getAlternateMobileNumber(),
                        request.getAdditionalMobileNumber() != null
                                ? request.getAdditionalMobileNumber()
                                : entity.getAdditionalMobileNumber()));
            }
            if (StringUtils.hasText(request.getAddressLine())) {
                entity.setAddressLine(request.getAddressLine().trim());
            }
            if (StringUtils.hasText(request.getCity())) {
                entity.setCity(request.getCity().trim());
            }
            if (StringUtils.hasText(request.getState())) {
                entity.setState(request.getState().trim());
            }
            if (StringUtils.hasText(request.getPincode())) {
                entity.setPincode(request.getPincode().trim());
            }
            if (request.getMapUrl() != null) {
                entity.setMapUrl(StringUtils.hasText(request.getMapUrl()) ? request.getMapUrl().trim() : null);
            }
            if (request.getLatitude() != null) {
                entity.setLatitude(request.getLatitude());
            }
            if (request.getLongitude() != null) {
                entity.setLongitude(request.getLongitude());
            }
            if (request.getGenderPolicy() != null) {
                entity.setGenderPolicy(request.getGenderPolicy());
            }
            if (request.getFoodIncludedListing() != null) {
                entity.setFoodIncludedListing(request.getFoodIncludedListing());
            }
            if (request.getSharingNotes() != null) {
                entity.setSharingNotes(
                        StringUtils.hasText(request.getSharingNotes()) ? request.getSharingNotes().trim() : null);
            }
            if (request.getUnmappedAmenities() != null) {
                entity.setUnmappedAmenities(
                        StringUtils.hasText(request.getUnmappedAmenities())
                                ? request.getUnmappedAmenities().trim()
                                : null);
            }
            return;
        }
        if (entityObj instanceof MessRegistrationEntity entity) {
            if (StringUtils.hasText(request.getOwnerName())) {
                entity.setOwnerName(request.getOwnerName().trim());
            }
            if (StringUtils.hasText(request.getMobileNumber())) {
                entity.setMobileNumber(request.getMobileNumber().trim());
            }
            if (request.getAlternateMobileNumber() != null || request.getMobileNumber() != null) {
                entity.setAlternateMobileNumber(RegistrationMobiles.resolveAlternate(
                        entity.getMobileNumber(),
                        request.getAlternateMobileNumber() != null
                                ? request.getAlternateMobileNumber()
                                : entity.getAlternateMobileNumber()));
            }
            if (request.getAdditionalMobileNumber() != null
                    || request.getAlternateMobileNumber() != null
                    || request.getMobileNumber() != null) {
                entity.setAdditionalMobileNumber(RegistrationMobiles.resolveAdditional(
                        entity.getMobileNumber(),
                        entity.getAlternateMobileNumber(),
                        request.getAdditionalMobileNumber() != null
                                ? request.getAdditionalMobileNumber()
                                : entity.getAdditionalMobileNumber()));
            }
            if (StringUtils.hasText(request.getAddressLine())) {
                entity.setAddressLine(request.getAddressLine().trim());
            }
            if (StringUtils.hasText(request.getCity())) {
                entity.setCity(request.getCity().trim());
            }
            if (StringUtils.hasText(request.getState())) {
                entity.setState(request.getState().trim());
            }
            if (StringUtils.hasText(request.getPincode())) {
                entity.setPincode(request.getPincode().trim());
            }
            if (request.getMapUrl() != null) {
                entity.setMapUrl(StringUtils.hasText(request.getMapUrl()) ? request.getMapUrl().trim() : null);
            }
            if (request.getLatitude() != null) {
                entity.setLatitude(request.getLatitude());
            }
            if (request.getLongitude() != null) {
                entity.setLongitude(request.getLongitude());
            }
            if (request.getGenderPolicy() != null) {
                entity.setGenderPolicy(request.getGenderPolicy());
            }
            if (request.getFoodIncludedListing() != null) {
                entity.setFoodIncludedListing(request.getFoodIncludedListing());
            }
            if (request.getSharingNotes() != null) {
                entity.setSharingNotes(
                        StringUtils.hasText(request.getSharingNotes()) ? request.getSharingNotes().trim() : null);
            }
            if (request.getUnmappedAmenities() != null) {
                entity.setUnmappedAmenities(
                        StringUtils.hasText(request.getUnmappedAmenities())
                                ? request.getUnmappedAmenities().trim()
                                : null);
            }
        }
    }

    private static List<AmenityAssignmentDto> toAmenityAssignments(PropertyRegistrationEntity entity) {
        List<AmenityAssignmentDto> list = new ArrayList<>();
        for (PropertyRegistrationAmenityEntity amenity : entity.getAmenities()) {
            AmenityAssignmentDto dto = new AmenityAssignmentDto();
            dto.setCode(amenity.getAmenityCode());
            dto.setLabel(amenity.getCustomLabel());
            list.add(dto);
        }
        return list;
    }

    private static String flattenAddress(String line, String city, String state, String pincode) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(line) && !"—".equals(line.trim())) {
            parts.add(line.trim());
        }
        if (StringUtils.hasText(city) && !"—".equals(city.trim())) {
            parts.add(city.trim());
        }
        if (StringUtils.hasText(state) && !"—".equals(state.trim())) {
            parts.add(state.trim());
        }
        if (StringUtils.hasText(pincode)
                && !(parts.isEmpty() && AdminLeadDefaults.PLACEHOLDER_PINCODE.equals(pincode.trim()))) {
            parts.add(pincode.trim());
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private UserEntity requireActiveUser(UUID userId) {
        return userRepository
                .findByIdAndIsActiveTrue(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private UserEntity requireLinkableOwner(UUID userId) {
        UserEntity user = requireActiveUser(userId);
        if (!user.isLinkableOwner()) {
            throw new BusinessException(
                    "OWNER_NOT_LINKABLE",
                    "Only an active ACOMI member account can be linked as owner.",
                    HttpStatus.CONFLICT);
        }
        return user;
    }

    private void applyOwnerLink(UUID existingLinkedOwnerUserId, UUID convertedSpaceId, UserEntity owner) {
        if (existingLinkedOwnerUserId != null && !existingLinkedOwnerUserId.equals(owner.getId())) {
            throw alreadyLinked();
        }
        if (convertedSpaceId == null) {
            return;
        }
        SpaceEntity space = spaceRepository
                .lockById(convertedSpaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", convertedSpaceId));
        UserEntity currentOwner = space.getOwner();
        if (currentOwner != null
                && !currentOwner.getId().equals(owner.getId())
                && existingLinkedOwnerUserId == null
                && currentOwner.getSystemRole() == SystemRole.USER) {
            throw alreadyLinked();
        }
        spaceService.transferOwnership(convertedSpaceId, owner.getId());
    }

    private static BusinessException alreadyLinked() {
        return new BusinessException(
                "OWNER_ALREADY_LINKED",
                "This listing already has a linked ACOMI owner.",
                HttpStatus.CONFLICT);
    }

    private UserEntity loadLinkedUser(UUID linkedOwnerUserId) {
        if (linkedOwnerUserId == null) {
            return null;
        }
        return userRepository.findById(linkedOwnerUserId).orElse(null);
    }

    private SpaceEntity loadConvertedSpace(UUID convertedSpaceId) {
        if (convertedSpaceId == null) {
            return null;
        }
        return spaceRepository.findById(convertedSpaceId).orElse(null);
    }

    private EnquiryAutoShareDecision listingDecision(SpaceEntity space) {
        if (space == null) {
            return null;
        }
        return enquiryAutoSharePolicy.evaluateListing(space);
    }

    /**
     * Admin-verified listings do not require a registered ACOMI user. Use an explicitly linked
     * owner when present; otherwise the acting admin is the provisional Space owner.
     */
    private UserEntity resolveOwnerForAdminPublish(UUID linkedOwnerUserId, UUID fallbackOwnerUserId) {
        if (linkedOwnerUserId != null) {
            return requireActiveUser(linkedOwnerUserId);
        }
        if (fallbackOwnerUserId != null) {
            return requireActiveUser(fallbackOwnerUserId);
        }
        throw new BusinessException(
                "Could not resolve a Space owner for this admin listing", HttpStatus.BAD_REQUEST);
    }

    private static String usableListingMobile(
            String primary, String alternate, String additional, UserEntity owner) {
        String adminMobile = owner != null && owner.isPlatformAdmin() ? blankMobile(owner.getMobileNumber()) : null;
        for (String candidate : List.of(
                primary == null ? "" : primary,
                alternate == null ? "" : alternate,
                additional == null ? "" : additional)) {
            String mobile = blankMobile(candidate);
            if (mobile != null && !isPlaceholderMobile(mobile) && !mobile.equals(adminMobile)) {
                return mobile;
            }
        }
        return null;
    }

    private static String blankMobile(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            return null;
        }
        return mobileNumber.trim();
    }

    private static boolean isPlaceholderMobile(String mobileNumber) {
        return AdminLeadDefaults.PLACEHOLDER_MOBILE.equals(mobileNumber);
    }

    private static void assertNotConverted(boolean convertedStatus, UUID convertedSpaceId) {
        if (convertedStatus || convertedSpaceId != null) {
            throw new BusinessException(
                    "Converted registrations cannot be modified", HttpStatus.CONFLICT);
        }
    }
}
