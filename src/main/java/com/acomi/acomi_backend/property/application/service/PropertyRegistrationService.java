package com.acomi.acomi_backend.property.application.service;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.property.api.dto.request.AdminCreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.request.CreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.application.service.SpaceAmenityService;
import com.acomi.acomi_backend.space.domain.model.AmenityCode;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
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

    @Transactional
    public PropertyRegistrationResponse registerPublic(
            CreatePropertyRegistrationRequest request, String requestIp) {
        otpService.consumeVerificationToken(
                request.getMobileNumber(), request.getVerificationToken(), OtpPurpose.PROPERTY_REGISTRATION);
        return registerVerified(PropertyRegistrationPayload.fromPublic(request), requestIp, LocalDateTime.now());
    }

    @Transactional
    public PropertyRegistrationResponse registerAdmin(AdminCreatePropertyRegistrationRequest request) {
        return registerAdminPayload(PropertyRegistrationPayload.fromAdmin(request));
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
            log.info("Property registration claimed admin lead reference={}", claimed.getReference());
            return toResponse(claimed);
        }

        PropertyRegistrationEntity saved =
                createNewLead(payload, requestIp, PropertyRegistrationSource.PUBLIC_WEBSITE, verifiedAt);
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
        boolean likelyDuplicate = propertyRegistrationRepository.existsLikelyDuplicate(
                payload.getMobileNumber(), payload.getPincode().trim(), payload.getPropertyName().trim());
        if (likelyDuplicate && existing.getStatus() != PropertyRegistrationStatus.CONVERTED) {
            existing.setStatus(PropertyRegistrationStatus.DUPLICATE);
        }
        return propertyRegistrationRepository.save(existing);
    }

    private PropertyRegistrationEntity createNewLead(
            PropertyRegistrationPayload payload,
            String requestIp,
            PropertyRegistrationSource source,
            LocalDateTime mobileVerifiedAt) {
        SpaceType propertyType = requireRegistrableType(payload.getPropertyType());
        String propertyName = payload.getPropertyName().trim();
        String pincode = payload.getPincode().trim();
        boolean likelyDuplicate = propertyRegistrationRepository.existsLikelyDuplicate(
                payload.getMobileNumber(), pincode, propertyName);

        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference(nextReference())
                .propertyType(propertyType)
                .propertyName(propertyName)
                .ownerName(payload.getOwnerName().trim())
                .mobileNumber(payload.getMobileNumber())
                .mobileVerifiedAt(mobileVerifiedAt)
                .description(trimToNull(payload.getDescription()))
                .addressLine(payload.getAddressLine().trim())
                .city(payload.getCity().trim())
                .state(payload.getState().trim())
                .pincode(pincode)
                .mapUrl(normalizeMapUrl(payload.getMapUrl()))
                .startingPrice(normalizePrice(payload.getStartingPrice()))
                .priceBasis(PriceBasis.forPropertyType(propertyType))
                .capacityEstimate(payload.getCapacityEstimate())
                .status(
                        likelyDuplicate
                                ? PropertyRegistrationStatus.DUPLICATE
                                : PropertyRegistrationStatus.PENDING)
                .source(source)
                .requestIp(requestIp)
                .testLead(Boolean.TRUE.equals(payload.getTestLead()))
                .build();

        attachAmenities(entity, propertyType, payload.getAmenities());
        return propertyRegistrationRepository.save(entity);
    }

    private void applyPayload(PropertyRegistrationEntity entity, PropertyRegistrationPayload payload) {
        SpaceType propertyType = requireRegistrableType(payload.getPropertyType());
        entity.setPropertyType(propertyType);
        entity.setPropertyName(payload.getPropertyName().trim());
        entity.setOwnerName(payload.getOwnerName().trim());
        entity.setMobileNumber(payload.getMobileNumber());
        entity.setDescription(trimToNull(payload.getDescription()));
        entity.setAddressLine(payload.getAddressLine().trim());
        entity.setCity(payload.getCity().trim());
        entity.setState(payload.getState().trim());
        entity.setPincode(payload.getPincode().trim());
        entity.setMapUrl(normalizeMapUrl(payload.getMapUrl()));
        entity.setStartingPrice(normalizePrice(payload.getStartingPrice()));
        entity.setPriceBasis(PriceBasis.forPropertyType(propertyType));
        entity.setCapacityEstimate(payload.getCapacityEstimate());
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

    private String normalizeMapUrl(String mapUrl) {
        String trimmed = trimToNull(mapUrl);
        if (trimmed == null) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new BusinessException("Map link must start with http:// or https://");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
}
