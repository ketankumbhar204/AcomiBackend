package com.acomi.acomi_backend.property.application.service;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.listing.api.dto.UpdateOwnedPropertyLeadRequest;
import com.acomi.acomi_backend.property.api.dto.request.CreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.registration.application.RegistrationOwnerLinker;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoCodec;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoService;
import com.acomi.acomi_backend.registration.application.RegistrationMobiles;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.application.service.ListingMapLocation;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyRegistrationService {

    private static final String REFERENCE_PREFIX = "PR";

    private final PropertyRegistrationRepository propertyRegistrationRepository;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final SpaceService spaceService;
    private final RegistrationPhotoService registrationPhotoService;
    private final RegistrationOwnerLinker registrationOwnerLinker;

    @Transactional
    public PropertyRegistrationResponse registerPublic(
            CreatePropertyRegistrationRequest request, String requestIp) {
        RegistrationPhotoCodec.decodeAll(request.getPhotos());
        otpService.consumeVerificationToken(
                request.getMobileNumber(), request.getVerificationToken(), OtpPurpose.PROPERTY_REGISTRATION);
        return registerVerified(PropertyRegistrationPayload.fromPublic(request), requestIp, LocalDateTime.now());
    }

    @Transactional
    public PropertyRegistrationResponse registerAdmin(AdminCreatePropertyRegistrationRequest request) {
        return registerAdminPayload(PropertyRegistrationPayload.fromAdmin(request));
    }

    @Transactional
    public PropertyRegistrationResponse registerImported(AdminCreatePropertyRegistrationRequest request) {
        PropertyRegistrationPayload normalized =
                AdminLeadDefaults.normalizeImportedProperty(PropertyRegistrationPayload.fromAdmin(request));
        PropertyRegistrationEntity saved = createNewLead(normalized, null, PropertyRegistrationSource.ADMIN, null);
        log.info(
                "Imported property registration created reference={} type={}",
                saved.getReference(),
                saved.getPropertyType());
        return toResponse(saved);
    }

    @Transactional
    public PropertyRegistrationResponse registerAdminPayload(PropertyRegistrationPayload payload) {
        PropertyRegistrationPayload normalized = AdminLeadDefaults.normalizeProperty(payload);
        PropertyRegistrationEntity saved = createNewLead(normalized, null, PropertyRegistrationSource.ADMIN, null);
        log.info(
                "Admin property registration created reference={} type={}",
                saved.getReference(),
                saved.getPropertyType());
        return toResponse(saved);
    }

    private PropertyRegistrationResponse registerVerified(
            PropertyRegistrationPayload payload, String requestIp, LocalDateTime verifiedAt) {
        SpaceType propertyType = requireRegistrableType(payload.getPropertyType());

        List<PropertyRegistrationEntity> claimCandidates =
                propertyRegistrationRepository.findUnclaimedAdminLeads(
                        payload.getMobileNumber(), propertyType, payload.getPropertyName().trim());
        if (claimCandidates.size() == 1) {
            PropertyRegistrationEntity claimed = claimAdminLead(claimCandidates.get(0), payload, requestIp, verifiedAt);
            registrationPhotoService.replaceForProperty(claimed.getId(), payload.getPhotos());
            log.info("Property registration claimed admin lead reference={}", claimed.getReference());
            return toResponse(claimed);
        }

        PropertyRegistrationEntity saved =
                createNewLead(payload, requestIp, PropertyRegistrationSource.PUBLIC_WEBSITE, verifiedAt);
        registrationPhotoService.replaceForProperty(saved.getId(), payload.getPhotos());
        log.info(
                "Property registration received reference={} type={} status={}",
                saved.getReference(),
                saved.getPropertyType(),
                saved.getStatus());
        return toResponse(saved);
    }

    private PropertyRegistrationEntity claimAdminLead(
            PropertyRegistrationEntity existing,
            PropertyRegistrationPayload payload,
            String requestIp,
            LocalDateTime verifiedAt) {
        applyPayload(existing, payload);
        existing.setMobileVerifiedAt(verifiedAt);
        existing.setClaimedAt(verifiedAt);
        existing.setClaimedVia(RegistrationClaimVia.PUBLIC_WEBSITE);
        if (StringUtils.hasText(requestIp)) {
            existing.setRequestIp(requestIp);
        }
        boolean likelyDuplicate = isLikelyDuplicate(payload);
        if (likelyDuplicate && existing.getStatus() != PropertyRegistrationStatus.CONVERTED) {
            existing.setStatus(PropertyRegistrationStatus.DUPLICATE);
        }
        userRepository
                .findByMobileNumberAndIsActiveTrue(payload.getMobileNumber())
                .ifPresent(user -> {
                    if (!user.isLinkableOwner()) {
                        return;
                    }
                    existing.setLinkedOwnerUserId(user.getId());
                    if (existing.getConvertedSpaceId() != null) {
                        spaceService.transferOwnership(existing.getConvertedSpaceId(), user.getId());
                    }
                });
        return propertyRegistrationRepository.save(existing);
    }

    private PropertyRegistrationEntity createNewLead(
            PropertyRegistrationPayload payload,
            String requestIp,
            PropertyRegistrationSource source,
            LocalDateTime mobileVerifiedAt) {
        SpaceType propertyType = requireRegistrableType(payload.getPropertyType());
        String propertyName = payload.getPropertyName().trim();
        String pincode = trimToNull(payload.getPincode());
        boolean likelyDuplicate = isLikelyDuplicate(payload);
        String alternateMobileNumber =
                RegistrationMobiles.resolveAlternate(payload.getMobileNumber(), payload.getAlternateMobileNumber());
        String additionalMobileNumber = RegistrationMobiles.resolveAdditional(
                payload.getMobileNumber(), alternateMobileNumber, payload.getAdditionalMobileNumber());
        ListingMapLocation.Resolved location = resolveLocation(payload);

        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference(nextReference())
                .propertyType(propertyType)
                .propertyName(propertyName)
                .buildingName(trimToNull(payload.getBuildingName()))
                .ownerName(textOrUnknown(payload.getOwnerName()))
                .mobileNumber(trimToNull(payload.getMobileNumber()))
                .alternateMobileNumber(alternateMobileNumber)
                .additionalMobileNumber(additionalMobileNumber)
                .mobileVerifiedAt(mobileVerifiedAt)
                .description(trimToNull(payload.getDescription()))
                .addressLine(trimToNull(payload.getAddressLine()))
                .city(trimToNull(payload.getCity()))
                .state(trimToNull(payload.getState()))
                .pincode(pincode)
                .latitude(location.latitude())
                .longitude(location.longitude())
                .mapUrl(location.mapUrl())
                .startingPrice(payload.getStartingPrice() == null ? null : normalizePrice(payload.getStartingPrice()))
                .priceBasis(priceBasisForNewLead(propertyType, payload))
                .capacityEstimate(payload.getCapacityEstimate())
                .status(
                        likelyDuplicate
                                ? PropertyRegistrationStatus.DUPLICATE
                                : PropertyRegistrationStatus.PENDING)
                .source(source)
                .requestIp(requestIp)
                .testLead(Boolean.TRUE.equals(payload.getTestLead()))
                .sharingNotes(trimToNull(payload.getSharingNotes()))
                .foodIncludedListing(payload.getFoodIncludedListing())
                .genderPolicy(payload.getGenderPolicy())
                .unmappedAmenities(trimToNull(payload.getUnmappedAmenities()))
                .build();

        attachAmenities(entity, propertyType, payload.getAmenities());
        if (source == PropertyRegistrationSource.PUBLIC_WEBSITE && mobileVerifiedAt != null) {
            entity.setLinkedOwnerUserId(registrationOwnerLinker.linkForVerifiedMobile(payload.getMobileNumber()));
        }
        return propertyRegistrationRepository.save(entity);
    }

    private void applyPayload(PropertyRegistrationEntity entity, PropertyRegistrationPayload payload) {
        SpaceType propertyType = requireRegistrableType(payload.getPropertyType());
        entity.setPropertyType(propertyType);
        entity.setPropertyName(payload.getPropertyName().trim());
        String buildingName = trimToNull(payload.getBuildingName());
        if (buildingName != null) {
            entity.setBuildingName(buildingName);
        }
        entity.setOwnerName(payload.getOwnerName().trim());
        entity.setMobileNumber(payload.getMobileNumber());
        entity.setAlternateMobileNumber(
                RegistrationMobiles.resolveAlternate(payload.getMobileNumber(), payload.getAlternateMobileNumber()));
        entity.setAdditionalMobileNumber(RegistrationMobiles.resolveAdditional(
                payload.getMobileNumber(),
                entity.getAlternateMobileNumber(),
                payload.getAdditionalMobileNumber()));
        entity.setDescription(trimToNull(payload.getDescription()));
        entity.setAddressLine(payload.getAddressLine().trim());
        entity.setCity(trimToNull(payload.getCity()));
        entity.setState(trimToNull(payload.getState()));
        entity.setPincode(trimToNull(payload.getPincode()));
        ListingMapLocation.Resolved location = resolveLocation(payload);
        entity.setLatitude(location.latitude());
        entity.setLongitude(location.longitude());
        entity.setMapUrl(location.mapUrl());
        entity.setStartingPrice(normalizePrice(payload.getStartingPrice()));
        entity.setPriceBasis(requirePriceBasis(propertyType, payload.getPriceBasis(), entity.getPriceBasis()));
        entity.setCapacityEstimate(payload.getCapacityEstimate());
        entity.setSharingNotes(trimToNull(payload.getSharingNotes()));
        entity.setFoodIncludedListing(payload.getFoodIncludedListing());
        entity.setGenderPolicy(payload.getGenderPolicy());
        entity.setUnmappedAmenities(trimToNull(payload.getUnmappedAmenities()));
        entity.getAmenities().clear();
        attachAmenities(entity, propertyType, payload.getAmenities());
    }

    private void attachAmenities(
            PropertyRegistrationEntity entity, SpaceType propertyType, List<AmenityAssignmentDto> requested) {
        if (!SpaceAmenityService.supportsAmenities(propertyType)) {
            return;
        }
        int order = 0;
        for (AmenityAssignmentDto amenity : SpaceAmenityService.normalizeAssignments(requested)) {
            boolean custom = AmenityCode.CUSTOM.name().equals(amenity.getCode());
            entity.addAmenity(PropertyRegistrationAmenityEntity.builder()
                    .amenityCode(amenity.getCode())
                    .customLabel(custom ? amenity.getLabel() : null)
                    .displayOrder(order++)
                    .build());
        }
    }

    private PriceBasis requirePriceBasis(SpaceType propertyType, PriceBasis requested, PriceBasis existing) {
        if (requested != null) {
            return requested;
        }
        if (existing != null) {
            return existing;
        }
        if (propertyType == SpaceType.CORPORATE_STAY) {
            throw new BusinessException(
                    "Corporate Stay needs an explicit price basis of per bed, per room, or per unit",
                    HttpStatus.BAD_REQUEST);
        }
        return PriceBasis.forPropertyType(propertyType);
    }

    private SpaceType requireRegistrableType(SpaceType propertyType) {
        if (propertyType == SpaceType.MESS) {
            throw new BusinessException(
                    "Mess registration is not available from this form", HttpStatus.BAD_REQUEST);
        }
        return propertyType;
    }

    private String nextReference() {
        long sequence = propertyRegistrationRepository.nextReferenceNumber();
        return "%s-%d-%06d".formatted(REFERENCE_PREFIX, Year.now().getValue(), sequence);
    }

    private PropertyRegistrationResponse toResponse(PropertyRegistrationEntity saved) {
        return PropertyRegistrationResponse.builder()
                .reference(saved.getReference())
                .priceBasis(saved.getPriceBasis())
                .submittedAt(saved.getCreatedAt())
                .build();
    }

    private BigDecimal normalizePrice(BigDecimal startingPrice) {
        return startingPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /** Keeps a submitted basis. A blank rent does not receive a generated basis. */
    private PriceBasis priceBasisForNewLead(SpaceType propertyType, PropertyRegistrationPayload payload) {
        if (payload.getPriceBasis() != null) {
            return payload.getPriceBasis();
        }
        if (payload.getStartingPrice() == null || propertyType == SpaceType.CORPORATE_STAY) {
            return null;
        }
        return PriceBasis.forPropertyType(propertyType);
    }

    private String textOrUnknown(String value) {
        return StringUtils.hasText(value) ? value.trim() : AdminLeadDefaults.UNKNOWN_OWNER;
    }

    private ListingMapLocation.Resolved resolveLocation(PropertyRegistrationPayload payload) {
        String rawMap = trimToNull(payload.getMapUrl());
        if (rawMap != null && !ListingMapLocation.looksLikeHttpUrl(rawMap)) {
            throw new BusinessException("Map link must start with http:// or https://");
        }
        return ListingMapLocation.resolve(payload.getLatitude(), payload.getLongitude(), rawMap);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** A typed location has no pincode, so it cannot match the locality duplicate check. */
    private boolean isLikelyDuplicate(PropertyRegistrationPayload payload) {
        String pincode = trimToNull(payload.getPincode());
        String propertyName = trimToNull(payload.getPropertyName());
        if (pincode == null || propertyName == null) {
            return false;
        }
        return propertyRegistrationRepository.existsLikelyDuplicate(
                payload.getMobileNumber(), pincode, propertyName);
    }

    /** Backward-compatible alias for the public controller. */
    @Transactional
    public PropertyRegistrationResponse register(
            CreatePropertyRegistrationRequest request, String requestIp) {
        return registerPublic(request, requestIp);
    }

    @Transactional(readOnly = true)
    public PropertyRegistrationEntity requireEntity(java.util.UUID id) {
        return propertyRegistrationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property registration", "id", id));
    }

    @Transactional
    public PropertyRegistrationEntity updateOwnedLead(UUID callerId, UUID id, UpdateOwnedPropertyLeadRequest request) {
        PropertyRegistrationEntity entity = requireOwnedEditable(callerId, id);
        if (StringUtils.hasText(request.getPropertyName())) {
            entity.setPropertyName(request.getPropertyName().trim());
        }
        if (request.getBuildingName() != null) {
            entity.setBuildingName(trimToNull(request.getBuildingName()));
        }
        if (StringUtils.hasText(request.getOwnerName())) {
            entity.setOwnerName(request.getOwnerName().trim());
        }
        if (request.getDescription() != null) {
            entity.setDescription(trimToNull(request.getDescription()));
        }
        if (StringUtils.hasText(request.getAddressLine())) {
            entity.setAddressLine(request.getAddressLine().trim());
        }
        if (request.getCity() != null) {
            entity.setCity(trimToNull(request.getCity()));
        }
        if (request.getState() != null) {
            entity.setState(trimToNull(request.getState()));
        }
        if (request.getPincode() != null) {
            entity.setPincode(trimToNull(request.getPincode()));
        }
        if (request.getLatitude() != null) {
            entity.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            entity.setLongitude(request.getLongitude());
        }
        if (request.getMapUrl() != null) {
            entity.setMapUrl(trimToNull(request.getMapUrl()));
        }
        if (request.getStartingPrice() != null) {
            entity.setStartingPrice(normalizePrice(request.getStartingPrice()));
        }
        if (request.getPriceBasis() != null) {
            entity.setPriceBasis(request.getPriceBasis());
        }
        if (request.getSharingNotes() != null) {
            entity.setSharingNotes(trimToNull(request.getSharingNotes()));
        }
        if (request.getFoodIncludedListing() != null) {
            entity.setFoodIncludedListing(request.getFoodIncludedListing());
        }
        if (request.getGenderPolicy() != null) {
            entity.setGenderPolicy(request.getGenderPolicy());
        }
        if (request.getAmenities() != null) {
            entity.getAmenities().clear();
            attachAmenities(entity, entity.getPropertyType(), request.getAmenities());
        }
        PropertyRegistrationEntity saved = propertyRegistrationRepository.save(entity);
        if (request.getPhotos() != null) {
            registrationPhotoService.replaceForProperty(saved.getId(), request.getPhotos());
        }
        return saved;
    }

    private PropertyRegistrationEntity requireOwnedEditable(UUID callerId, UUID id) {
        PropertyRegistrationEntity entity = requireEntity(id);
        if (entity.getLinkedOwnerUserId() == null || !entity.getLinkedOwnerUserId().equals(callerId)) {
            throw new ResourceNotFoundException("Property registration", "id", id);
        }
        if (entity.getConvertedSpaceId() != null || !ownerMayEdit(entity.getStatus())) {
            throw new BusinessException(
                    "REGISTRATION_NOT_EDITABLE",
                    "This registration can no longer be edited.",
                    HttpStatus.CONFLICT);
        }
        return entity;
    }

    private static boolean ownerMayEdit(PropertyRegistrationStatus status) {
        return status == PropertyRegistrationStatus.PENDING
                || status == PropertyRegistrationStatus.IN_REVIEW
                || status == PropertyRegistrationStatus.CONTACTED;
    }
}
