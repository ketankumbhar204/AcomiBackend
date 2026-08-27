package com.acomi.acomi_backend.mess.application.service;

import com.acomi.acomi_backend.auth.application.service.OtpService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.mess.api.dto.request.CreateMessRegistrationRequest;
import com.acomi.acomi_backend.mess.api.dto.response.MessRegistrationResponse;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationSource;
import com.acomi.acomi_backend.mess.domain.model.MessRegistrationStatus;
import com.acomi.acomi_backend.mess.infrastructure.persistence.entity.MessRegistrationEntity;
import com.acomi.acomi_backend.mess.infrastructure.persistence.repository.MessRegistrationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Year;
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
    public MessRegistrationResponse register(CreateMessRegistrationRequest request, String requestIp) {
        otpService.consumeVerificationToken(
                request.getMobileNumber(), request.getVerificationToken(), OtpPurpose.MESS_REGISTRATION);
        LocalDateTime verifiedAt = LocalDateTime.now();

        String messName = request.getMessName().trim();
        String pincode = request.getPincode().trim();
        boolean likelyDuplicate = messRegistrationRepository.existsLikelyDuplicate(
                request.getMobileNumber(), pincode, messName);

        MessRegistrationEntity entity = MessRegistrationEntity.builder()
                .reference(nextReference())
                .messName(messName)
                .ownerName(request.getOwnerName().trim())
                .mobileNumber(request.getMobileNumber())
                .mobileVerifiedAt(verifiedAt)
                .description(trimToNull(request.getDescription()))
                .addressLine(request.getAddressLine().trim())
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .pincode(pincode)
                .mapUrl(normalizeMapUrl(request.getMapUrl()))
                .monthlyPrice(normalizePrice(request.getMonthlyPrice()))
                .mealPrice(normalizePrice(request.getMealPrice()))
                .capacityEstimate(request.getCapacityEstimate())
                .status(
                        likelyDuplicate
                                ? MessRegistrationStatus.DUPLICATE
                                : MessRegistrationStatus.PENDING)
                .source(MessRegistrationSource.PUBLIC_WEBSITE)
                .requestIp(requestIp)
                .build();

        MessRegistrationEntity saved = messRegistrationRepository.save(entity);
        log.info(
                "Mess registration received reference={} status={}",
                saved.getReference(),
                saved.getStatus());

        return MessRegistrationResponse.builder()
                .reference(saved.getReference())
                .submittedAt(saved.getCreatedAt())
                .build();
    }

    private String nextReference() {
        long sequence = messRegistrationRepository.nextReferenceNumber();
        return "%s-%d-%06d".formatted(REFERENCE_PREFIX, Year.now().getValue(), sequence);
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
}
