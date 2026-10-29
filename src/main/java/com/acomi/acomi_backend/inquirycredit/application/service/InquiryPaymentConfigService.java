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
import com.acomi.acomi_backend.storage.application.service.StoredFileService;
import com.acomi.acomi_backend.storage.domain.model.FilePurpose;
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

        return toResponse(config, packages, resolveQr(callerId, config.getQrFileId()));
    }

    @Transactional(readOnly = true)
    public InquiryPaymentConfigResponse adminGet(UUID callerId) {
        InquiryPaymentConfigEntity config = requireConfig();
        List<InquiryCreditPackageResponse> packages = packageRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(InquiryCreditPackageResponse::from)
                .toList();
        return toResponse(config, packages, resolveQr(callerId, config.getQrFileId()));
    }

    @Transactional
    public void adminUpdate(UpdatePaymentConfigRequest request, UUID adminId) {
        InquiryPaymentConfigEntity config = requireConfig();

        if (request.getUpiId() != null) {
            config.setUpiId(request.getUpiId().isBlank() ? null : request.getUpiId().trim());
        }
        if (request.getQrFileId() != null) {
            if (request.getQrFileId().equals(config.getQrFileId())) {
                storedFileService.tryRetainInquiryPaymentQr(request.getQrFileId());
            }
            storedFileService.requireActiveForAssociation(
                    adminId, request.getQrFileId(), FilePurpose.INQUIRY_PAYMENT_QR, null);
            storedFileService.replaceAssociation(config.getQrFileId(), request.getQrFileId());
            config.setQrFileId(request.getQrFileId());
        } else if (Boolean.TRUE.equals(request.getClearQr()) && config.getQrFileId() != null) {
            storedFileService.scheduleDelete(config.getQrFileId());
            config.setQrFileId(null);
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
        if (request.getViewHereDailyLimit() != null) {
            if (request.getViewHereDailyLimit() < 0) {
                throw new BusinessException(
                        "INVALID_VIEW_HERE_DAILY_LIMIT",
                        "View here daily limit must be 0 or greater.",
                        HttpStatus.BAD_REQUEST);
            }
            config.setViewHereDailyLimit(request.getViewHereDailyLimit());
        }
        if (request.getWebViewHereEnabled() != null) {
            config.setWebViewHereEnabled(request.getWebViewHereEnabled());
        }
        if (request.getAndroidViewHereEnabled() != null) {
            config.setAndroidViewHereEnabled(request.getAndroidViewHereEnabled());
        }
        if (request.getAndroidViewHereDailyLimit() != null) {
            if (request.getAndroidViewHereDailyLimit() < 0) {
                throw new BusinessException(
                        "INVALID_ANDROID_VIEW_HERE_DAILY_LIMIT",
                        "Mobile contact-view daily limit must be 0 or greater.",
                        HttpStatus.BAD_REQUEST);
            }
            config.setAndroidViewHereDailyLimit(request.getAndroidViewHereDailyLimit());
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
            ResolvedQr qr) {
        return InquiryPaymentConfigResponse.builder()
                .configId(config.getId())
                .enabled(config.isEnabled())
                .upiId(config.getUpiId())
                .qrFileId(qr.fileId())
                .qrUrl(qr.url())
                .whatsappNumber(config.getWhatsappNumber())
                .instructions(config.getInstructions())
                .webFreeDailyLimit(config.getWebFreeDailyLimit())
                .viewHereDailyLimit(config.getViewHereDailyLimit())
                .webViewHereEnabled(config.isWebViewHereEnabled())
                .androidViewHereEnabled(config.isAndroidViewHereEnabled())
                .androidViewHereDailyLimit(config.getAndroidViewHereDailyLimit())
                .androidBillingMode(AndroidInquiryBillingMode.fromDb(config.getAndroidBillingMode()))
                .androidFreeDailyLimit(config.getAndroidFreeDailyLimit())
                .androidHourlyRateLimit(config.getAndroidHourlyRateLimit())
                .packages(packages)
                .build();
    }

    private ResolvedQr resolveQr(UUID callerId, UUID qrFileId) {
        if (qrFileId == null || callerId == null) {
            return ResolvedQr.none();
        }
        try {
            if (!storedFileService.tryRetainInquiryPaymentQr(qrFileId)) {
                log.warn("inquiry_qr_unavailable qrFileId={}", qrFileId);
                return ResolvedQr.none();
            }
            String url = storedFileService.resolveContentUrlIsolated(callerId, qrFileId);
            return new ResolvedQr(qrFileId, url);
        } catch (RuntimeException ex) {
            log.warn(
                    "inquiry_qr_url_resolution_failed callerId={} qrFileId={}",
                    callerId,
                    qrFileId,
                    ex);
            return new ResolvedQr(qrFileId, null);
        }
    }

    private record ResolvedQr(UUID fileId, String url) {
        static ResolvedQr none() {
            return new ResolvedQr(null, null);
        }
    }
}
