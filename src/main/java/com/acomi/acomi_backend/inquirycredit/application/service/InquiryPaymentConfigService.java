package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.UpdatePaymentConfigRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPackageResponse;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryPaymentConfigResponse;
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
    public InquiryPaymentConfigResponse getPublicConfig(UUID callerId) {
        InquiryPaymentConfigEntity config = requireConfig();
        List<InquiryCreditPackageResponse> packages = packageRepository
                .findByEnabledTrueOrderByDisplayOrderAsc()
                .stream()
                .map(InquiryCreditPackageResponse::from)
                .toList();

        String qrUrl = resolveQrUrl(callerId, config.getQrFileId());

        return InquiryPaymentConfigResponse.builder()
                .configId(config.getId())
                .enabled(config.isEnabled())
                .upiId(config.getUpiId())
                .qrFileId(config.getQrFileId())
                .qrUrl(qrUrl)
                .whatsappNumber(config.getWhatsappNumber())
                .instructions(config.getInstructions())
                .packages(packages)
                .build();
    }

    @Transactional(readOnly = true)
    public InquiryPaymentConfigResponse adminGet(UUID callerId) {
        return getPublicConfig(callerId);
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
        config.setUpdatedByUserId(adminId);
        configRepository.save(config);

        log.info("inquiry_payment_config_updated adminId={}", adminId);
        return getPublicConfig(adminId);
    }

    private InquiryPaymentConfigEntity requireConfig() {
        return configRepository
                .findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new BusinessException(
                        "INQUIRY_PAYMENT_CONFIG_MISSING",
                        "Inquiry payment configuration has not been initialized.",
                        HttpStatus.INTERNAL_SERVER_ERROR));
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
