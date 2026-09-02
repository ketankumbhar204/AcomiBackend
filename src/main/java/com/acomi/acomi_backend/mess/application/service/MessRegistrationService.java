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
import com.acomi.acomi_backend.registration.application.AdminLeadDefaults;
import com.acomi.acomi_backend.registration.application.RegistrationMobiles;
import com.acomi.acomi_backend.registration.domain.model.RegistrationClaimVia;
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

    @Transactional
    public MessRegistrationResponse registerPublic(CreateMessRegistrationRequest request, String requestIp) {
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
            log.info("Mess registration claimed admin lead reference={}", claimed.getReference());
            return toResponse(claimed);
        }

        MessRegistrationEntity saved =
                createNewLead(payload, requestIp, MessRegistrationSource.PUBLIC_WEBSITE, verifiedAt);
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
        boolean likelyDuplicate = messRegistrationRepository.existsLikelyDuplicate(
                payload.getMobileNumber(), payload.getPincode().trim(), payload.getMessName().trim());
        if (likelyDuplicate && existing.getStatus() != MessRegistrationStatus.CONVERTED) {
            existing.setStatus(MessRegistrationStatus.DUPLICATE);
        }
        return messRegistrationRepository.save(existing);
    }

    private MessRegistrationEntity createNewLead(
            MessRegistrationPayload payload,
            String requestIp,
            MessRegistrationSource source,
            LocalDateTime mobileVerifiedAt) {
        String messName = payload.getMessName().trim();
        String pincode = payload.getPincode().trim();
        boolean likelyDuplicate =
                messRegistrationRepository.existsLikelyDuplicate(payload.getMobileNumber(), pincode, messName);
        String alternateMobileNumber =
                RegistrationMobiles.resolveAlternate(payload.getMobileNumber(), payload.getAlternateMobileNumber());

        MessRegistrationEntity entity = MessRegistrationEntity.builder()
                .reference(nextReference())
                .messName(messName)
                .ownerName(payload.getOwnerName().trim())
                .mobileNumber(payload.getMobileNumber())
                .alternateMobileNumber(alternateMobileNumber)
                .mobileVerifiedAt(mobileVerifiedAt)
                .description(trimToNull(payload.getDescription()))
                .addressLine(payload.getAddressLine().trim())
                .city(payload.getCity().trim())
                .state(payload.getState().trim())
                .pincode(pincode)
                .mapUrl(normalizeMapUrl(payload.getMapUrl()))
                .monthlyPrice(normalizePrice(payload.getMonthlyPrice()))
                .mealPrice(normalizePrice(payload.getMealPrice()))
                .capacityEstimate(payload.getCapacityEstimate())
                .status(
                        likelyDuplicate ? MessRegistrationStatus.DUPLICATE : MessRegistrationStatus.PENDING)
                .source(source)
                .requestIp(requestIp)
                .testLead(Boolean.TRUE.equals(payload.getTestLead()))
                .build();

        return messRegistrationRepository.save(entity);
    }

    private void applyPayload(MessRegistrationEntity entity, MessRegistrationPayload payload) {
        entity.setMessName(payload.getMessName().trim());
        entity.setOwnerName(payload.getOwnerName().trim());
        entity.setMobileNumber(payload.getMobileNumber());
        entity.setAlternateMobileNumber(
                RegistrationMobiles.resolveAlternate(payload.getMobileNumber(), payload.getAlternateMobileNumber()));
        entity.setDescription(trimToNull(payload.getDescription()));
        entity.setAddressLine(payload.getAddressLine().trim());
        entity.setCity(payload.getCity().trim());
        entity.setState(payload.getState().trim());
        entity.setPincode(payload.getPincode().trim());
        entity.setMapUrl(normalizeMapUrl(payload.getMapUrl()));
        entity.setMonthlyPrice(normalizePrice(payload.getMonthlyPrice()));
        entity.setMealPrice(normalizePrice(payload.getMealPrice()));
        entity.setCapacityEstimate(payload.getCapacityEstimate());
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

    private String normalizeMapUrl(String mapUrl) {
        if (!StringUtils.hasText(mapUrl)) {
            return null;
        }
        String trimmed = mapUrl.trim();
        String lower = trimmed.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return null;
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
