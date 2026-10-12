package com.acomi.acomi_backend.mess.application.service;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.mess.api.dto.request.AdminCreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.request.CreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.registration.application.RegistrationMobiles;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoCodec;
import com.acomi.acomi_backend.registration.application.RegistrationPhotoService;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
import com.acomi.acomi_backend.space.application.service.ListingMapLocation;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessRegistrationService {

    private static final String REFERENCE_PREFIX = "MR";

    private final MessRegistrationRepository messRegistrationRepository;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final SpaceService spaceService;
    private final RegistrationPhotoService registrationPhotoService;

    @Transactional
    public MessRegistrationResponse registerPublic(CreateMessRegistrationRequest request, String requestIp) {
        RegistrationPhotoCodec.decodeAll(request.getPhotos());
        otpService.consumeVerificationToken(
                request.getMobileNumber(), request.getVerificationToken(), OtpPurpose.MESS_REGISTRATION);
        return registerVerified(MessRegistrationPayload.fromPublic(request), requestIp, LocalDateTime.now());
    }

    @Transactional
    public MessRegistrationResponse registerAdmin(AdminCreateMessRegistrationRequest request) {
        return registerAdminPayload(MessRegistrationPayload.fromAdmin(request));
    }

    @Transactional
    public MessRegistrationResponse registerAdminPayload(MessRegistrationPayload payload) {
        MessRegistrationPayload normalized = AdminLeadDefaults.normalizeMess(payload);
        MessRegistrationEntity saved = createNewLead(normalized, null, MessRegistrationSource.ADMIN, null);
        log.info("Admin mess registration created reference={}", saved.getReference());
        return toResponse(saved);
    }

    private MessRegistrationResponse registerVerified(
            MessRegistrationPayload payload, String requestIp, LocalDateTime verifiedAt) {
        List<MessRegistrationEntity> claimCandidates = messRegistrationRepository.findUnclaimedAdminLeads(
                payload.getMobileNumber(), payload.getMessName().trim());
        if (claimCandidates.size() == 1) {
            MessRegistrationEntity claimed = claimAdminLead(claimCandidates.get(0), payload, requestIp, verifiedAt);
            registrationPhotoService.replaceForMess(claimed.getId(), payload.getPhotos());
            log.info("Mess registration claimed admin lead reference={}", claimed.getReference());
            return toResponse(claimed);
        }

        MessRegistrationEntity saved =
                createNewLead(payload, requestIp, MessRegistrationSource.PUBLIC_WEBSITE, verifiedAt);
        registrationPhotoService.replaceForMess(saved.getId(), payload.getPhotos());
        log.info("Mess registration received reference={} status={}", saved.getReference(), saved.getStatus());
        return toResponse(saved);
    }

    private MessRegistrationEntity claimAdminLead(
            MessRegistrationEntity existing,
            MessRegistrationPayload payload,
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
        if (likelyDuplicate && existing.getStatus() != MessRegistrationStatus.CONVERTED) {
            existing.setStatus(MessRegistrationStatus.DUPLICATE);
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
        return messRegistrationRepository.save(existing);
    }

    private MessRegistrationEntity createNewLead(
            MessRegistrationPayload payload,
            String requestIp,
            MessRegistrationSource source,
            LocalDateTime mobileVerifiedAt) {
        String messName = payload.getMessName().trim();
        String pincode = trimToNull(payload.getPincode());
        boolean likelyDuplicate = isLikelyDuplicate(payload);
        String alternateMobileNumber =
                RegistrationMobiles.resolveAlternate(payload.getMobileNumber(), payload.getAlternateMobileNumber());
        String additionalMobileNumber = RegistrationMobiles.resolveAdditional(
                payload.getMobileNumber(), alternateMobileNumber, payload.getAdditionalMobileNumber());
        ListingMapLocation.Resolved location = ListingMapLocation.resolve(
                payload.getLatitude(), payload.getLongitude(), payload.getMapUrl());

        MessRegistrationEntity entity = MessRegistrationEntity.builder()
                .reference(nextReference())
                .messName(messName)
                .buildingName(trimToNull(payload.getBuildingName()))
                .ownerName(payload.getOwnerName().trim())
                .mobileNumber(payload.getMobileNumber())
                .alternateMobileNumber(alternateMobileNumber)
                .additionalMobileNumber(additionalMobileNumber)
                .mobileVerifiedAt(mobileVerifiedAt)
                .description(trimToNull(payload.getDescription()))
                .addressLine(payload.getAddressLine().trim())
                .city(trimToNull(payload.getCity()))
                .state(trimToNull(payload.getState()))
                .pincode(pincode)
                .latitude(location.latitude())
                .longitude(location.longitude())
                .mapUrl(location.mapUrl())
                .monthlyPrice(normalizePrice(payload.getMonthlyPrice()))
                .monthlyPriceBasis(PriceBasis.resolve(SpaceType.MESS, payload.getMonthlyPriceBasis()))
                .mealPrice(normalizePrice(payload.getMealPrice()))
                .capacityEstimate(payload.getCapacityEstimate())
                .status(
                        likelyDuplicate ? MessRegistrationStatus.DUPLICATE : MessRegistrationStatus.PENDING)
                .source(source)
                .requestIp(requestIp)
                .testLead(Boolean.TRUE.equals(payload.getTestLead()))
                .sharingNotes(trimToNull(payload.getSharingNotes()))
                .foodIncludedListing(payload.getFoodIncludedListing())
                .genderPolicy(payload.getGenderPolicy())
                .unmappedAmenities(trimToNull(payload.getUnmappedAmenities()))
                .build();

        return messRegistrationRepository.save(entity);
    }

    private void applyPayload(MessRegistrationEntity entity, MessRegistrationPayload payload) {
        entity.setMessName(payload.getMessName().trim());
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
        ListingMapLocation.Resolved location = ListingMapLocation.resolve(
                payload.getLatitude(), payload.getLongitude(), payload.getMapUrl());
        entity.setLatitude(location.latitude());
        entity.setLongitude(location.longitude());
        entity.setMapUrl(location.mapUrl());
        entity.setMonthlyPrice(normalizePrice(payload.getMonthlyPrice()));
        entity.setMonthlyPriceBasis(
                payload.getMonthlyPriceBasis() != null
                        ? payload.getMonthlyPriceBasis()
                        : entity.getMonthlyPriceBasis() != null
                                ? entity.getMonthlyPriceBasis()
                                : PriceBasis.PER_UNIT);
        entity.setMealPrice(normalizePrice(payload.getMealPrice()));
        entity.setCapacityEstimate(payload.getCapacityEstimate());
        entity.setSharingNotes(trimToNull(payload.getSharingNotes()));
        entity.setFoodIncludedListing(payload.getFoodIncludedListing());
        entity.setGenderPolicy(payload.getGenderPolicy());
        entity.setUnmappedAmenities(trimToNull(payload.getUnmappedAmenities()));
    }

    private String nextReference() {
        long sequence = messRegistrationRepository.nextReferenceNumber();
        return "%s-%d-%06d".formatted(REFERENCE_PREFIX, Year.now().getValue(), sequence);
    }

    private MessRegistrationResponse toResponse(MessRegistrationEntity saved) {
        return MessRegistrationResponse.builder()
                .reference(saved.getReference())
                .submittedAt(saved.getCreatedAt())
                .build();
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /** A typed location has no pincode, so it cannot match the locality duplicate check. */
    private boolean isLikelyDuplicate(MessRegistrationPayload payload) {
        String pincode = trimToNull(payload.getPincode());
        String messName = trimToNull(payload.getMessName());
        if (pincode == null || messName == null) {
            return false;
        }
        return messRegistrationRepository.existsLikelyDuplicate(
                payload.getMobileNumber(), pincode, messName);
    }

    @Transactional
    public MessRegistrationResponse register(CreateMessRegistrationRequest request, String requestIp) {
        return registerPublic(request, requestIp);
    }

    @Transactional(readOnly = true)
    public MessRegistrationEntity requireEntity(java.util.UUID id) {
        return messRegistrationRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mess registration", "id", id));
    }
}
