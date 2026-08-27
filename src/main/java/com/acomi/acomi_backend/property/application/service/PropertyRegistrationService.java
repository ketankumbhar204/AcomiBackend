package com.acomi.acomi_backend.property.application.service;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.property.api.dto.request.CreatePropertyRegistrationRequest;
import com.acomi.acomi_backend.property.api.dto.response.PropertyRegistrationResponse;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationSource;
import com.acomi.acomi_backend.property.domain.model.PropertyRegistrationStatus;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationAmenityEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.entity.PropertyRegistrationEntity;
import com.acomi.acomi_backend.property.infrastructure.persistence.repository.PropertyRegistrationRepository;
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
    public PropertyRegistrationResponse register(
            CreatePropertyRegistrationRequest request, String requestIp) {
        SpaceType propertyType = requireRegistrableType(request.getPropertyType());

        // Purpose-bound and mobile-bound. Also enforces expiry and single use, so a token minted
        // for LOGIN or for a different number cannot create a lead here.
        otpService.consumeVerificationToken(
                request.getMobileNumber(), request.getVerificationToken(), OtpPurpose.PROPERTY_REGISTRATION);
        LocalDateTime verifiedAt = LocalDateTime.now();

        String propertyName = request.getPropertyName().trim();
        String pincode = request.getPincode().trim();
        boolean likelyDuplicate = propertyRegistrationRepository.existsLikelyDuplicate(
                request.getMobileNumber(), pincode, propertyName);

        PropertyRegistrationEntity entity = PropertyRegistrationEntity.builder()
                .reference(nextReference())
                .propertyType(propertyType)
                .propertyName(propertyName)
                .ownerName(request.getOwnerName().trim())
                .mobileNumber(request.getMobileNumber())
                .mobileVerifiedAt(verifiedAt)
                .description(trimToNull(request.getDescription()))
                .addressLine(request.getAddressLine().trim())
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .pincode(pincode)
                .mapUrl(normalizeMapUrl(request.getMapUrl()))
                .startingPrice(normalizePrice(request.getStartingPrice()))
                .priceBasis(PriceBasis.forPropertyType(propertyType))
                .capacityEstimate(request.getCapacityEstimate())
                // A duplicate is flagged for staff triage, never dropped: the owner still expects
                // a callback and the newer submission may carry corrected details.
                .status(
                        likelyDuplicate
                                ? PropertyRegistrationStatus.DUPLICATE
                                : PropertyRegistrationStatus.PENDING)
                .source(PropertyRegistrationSource.PUBLIC_WEBSITE)
                .requestIp(requestIp)
                .build();

        attachAmenities(entity, propertyType, request.getAmenities());

        PropertyRegistrationEntity saved = propertyRegistrationRepository.save(entity);
        log.info(
                "Property registration received reference={} type={} status={}",
                saved.getReference(),
                saved.getPropertyType(),
                saved.getStatus());

        return PropertyRegistrationResponse.builder()
                .reference(saved.getReference())
                .priceBasis(saved.getPriceBasis())
                .submittedAt(saved.getCreatedAt())
                .build();
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

    private BigDecimal normalizePrice(BigDecimal startingPrice) {
        return startingPrice.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Only http(s) links are stored. Anything else could render as a javascript: or data: URL in a
     * future staff console.
     */
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
}
