package com.acomi.acomi_backend.inquirycredit.application.service;

import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.inquirycredit.api.dto.request.UpdateCreditPackageRequest;
import com.acomi.acomi_backend.inquirycredit.api.dto.response.InquiryCreditPackageResponse;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPackageEntity;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.repository.InquiryCreditPackageRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryCreditPackageAdminService {

    private final InquiryCreditPackageRepository packageRepository;

    @Transactional(readOnly = true)
    public List<InquiryCreditPackageResponse> listAll() {
        return packageRepository.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .map(InquiryCreditPackageResponse::from)
                .toList();
    }

    @Transactional
    public InquiryCreditPackageResponse update(UUID id, UpdateCreditPackageRequest request, UUID adminId) {
        InquiryCreditPackageEntity pkg = packageRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InquiryCreditPackage", "id", id));

        if (request.getName() != null && !request.getName().isBlank()) {
            pkg.setName(request.getName().trim());
        }
        if (request.getPriceAmount() != null) {
            pkg.setPriceAmount(request.getPriceAmount());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            pkg.setCurrency(request.getCurrency().trim().toUpperCase());
        }
        if (request.getCredits() != null && request.getCredits() > 0) {
            pkg.setCredits(request.getCredits());
        }
        if (request.getEnabled() != null) {
            pkg.setEnabled(request.getEnabled());
        }
        if (request.getDisplayOrder() != null) {
            pkg.setDisplayOrder(request.getDisplayOrder());
        }

        pkg = packageRepository.save(pkg);
        log.info("inquiry_credit_package_updated id={} adminId={}", id, adminId);
        return InquiryCreditPackageResponse.from(pkg);
    }
}
