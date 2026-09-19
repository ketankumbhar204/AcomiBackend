package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.UpdatePaymentConfigRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPackageResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryPaymentConfigResponse;
import com.acomi.acomi_backend.inquirycredit.domain.model.AndroidInquiryBillingMode;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryPaymentConfigEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPackageRepository;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryPaymentConfigRepository;
import com.acomi.acomi_backend.storage.api.dto.response.ContentUrlResponse;
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryPaymentConfigService {

    private final InquiryPaymentConfigRepository configRepository;
    private final InquiryCreditPackageRepository packageRepository;
    private final StoredFileService storedFileService;

    @Transactional(readOnly = true)
    public InquiryPaymentConfigResponse getPublicConfig(UUID callerId, InquiryClientChannel channel) {
        InquiryPaymentConfigEntity config = requireConfig();
        InquiryClientChannel safeChannel = channel != null ? channel : InquiryClientChannel.WEB;
        List<InquiryCreditPackageResponse> packages = packageRepository
                .findByEnabledTrueAndClientChannelOrderByDisplayOrderAsc(safeChannel)
                .stream()
                .map(InquiryCreditPackageResponse::from)
                .toList();

        return toResponse(config, packages, resolveQrUrl(callerId, config.getQrFileId()));
    }

    @Transactional(readOnly = true)
    public InquiryPaymentConfigResponse adminGet(UUID callerId) {
        InquiryPaymentConfigEntity config = requireConfig();
        List<InquiryCreditPackageResponse> packages = packageRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(InquiryCreditPackageResponse::from)
                .toList();
        return toResponse(config, packages, resolveQrUrl(callerId, config.getQrFileId()));
    }

    @Transactional
    public InquiryPaymentConfigResponse adminUpdate(UpdatePaymentConfigRequest request, UUID adminId) {
        InquiryPaymentConfigEntity config = requireConfig();

        if (request.getUpiId() != null) {
            config.setUpiId(request.getUpiId().isBlank() ? null : request.getUpiId().trim());
        }
        if (request.getQrFileId() != null) {
            config.setQrFileId(request.getQrFileId());
        }
        if (request.getWhatsappNumber() != null) {
            config.setWhatsappNumber(
                    request.getWhatsappNumber().isBlank() ? null : request.getWhatsappNumber().trim());
        }
        if (request.getInstructions() != null) {
            config.setInstructions(
                    request.getInstructions().isBlank() ? null : request.getInstructions().trim());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getWebFreeDailyLimit() != null) {
            if (request.getWebFreeDailyLimit() < 0) {
                throw new BusinessException(
                        "INVALID_WEB_FREE_DAILY_LIMIT",
                        "Web free daily limit must be 0 or greater.",
                        HttpStatus.BAD_REQUEST);
            }
            config.setWebFreeDailyLimit(request.getWebFreeDailyLimit());
        }
        if (request.getAndroidBillingMode() != null) {
            AndroidInquiryBillingMode mode =
                    AndroidInquiryBillingMode.fromDb(request.getAndroidBillingMode());
            config.setAndroidBillingMode(mode.name());
        }
        if (request.getAndroidFreeDailyLimit() != null) {
            if (request.getAndroidFreeDailyLimit() < 0) {
                throw new BusinessException(
                        "INVALID_ANDROID_FREE_DAILY_LIMIT",
                        "Android free daily limit must be 0 or greater.",
                        HttpStatus.BAD_REQUEST);
            }
            config.setAndroidFreeDailyLimit(request.getAndroidFreeDailyLimit());
        }
        if (request.getAndroidHourlyRateLimit() != null) {
            if (request.getAndroidHourlyRateLimit() < 1) {
                throw new BusinessException(
                        "INVALID_ANDROID_HOURLY_RATE_LIMIT",
                        "Android hourly rate limit must be at least 1.",
                        HttpStatus.BAD_REQUEST);
            }
            config.setAndroidHourlyRateLimit(request.getAndroidHourlyRateLimit());
        }
        config.setUpdatedByUserId(adminId);
        configRepository.save(config);

        log.info("inquiry_payment_config_updated adminId={}", adminId);
        return adminGet(adminId);
    }

    @Transactional(readOnly = true)
    public InquiryPaymentConfigEntity requireConfig() {
        return configRepository
                .findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new BusinessException(
                        "INQUIRY_PAYMENT_CONFIG_MISSING",
                        "Inquiry payment configuration has not been initialized.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private InquiryPaymentConfigResponse toResponse(
            InquiryPaymentConfigEntity config,
            List<InquiryCreditPackageResponse> packages,
            String qrUrl) {
        return InquiryPaymentConfigResponse.builder()
                .configId(config.getId())
                .enabled(config.isEnabled())
                .upiId(config.getUpiId())
                .qrFileId(config.getQrFileId())
                .qrUrl(qrUrl)
                .whatsappNumber(config.getWhatsappNumber())
                .instructions(config.getInstructions())
                .webFreeDailyLimit(config.getWebFreeDailyLimit())
                .androidBillingMode(AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode()))
                .androidFreeDailyLimit(config.getAndroidFreeDailyLimit())
                .androidHourlyRateLimit(config.getAndroidHourlyRateLimit())
                .packages(packages)
                .build();
    }

    private String resolveQrUrl(UUID callerId, UUID qrFileId) {
        if (qrFileId == null || callerId == null) {
            return null;
        }
        try {
            ContentUrlResponse response = storedFileService.createContentUrl(callerId, qrFileId);
            return response.getContentUrl();
        } catch (RuntimeException ex) {
            log.warn("inquiry_qr_url_resolution_failed callerId={} qrFileId={}", callerId, qrFileId);
            return null;
        }
    }
}
