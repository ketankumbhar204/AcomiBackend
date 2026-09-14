package com.acomi.acomi_backend.payment.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.dashboard.application.service.DashboardAccessService;
import com.acomi.acomi_backend.payment.api.dto.request.UpdateSpaceBillingSettingsRequest;
import com.acomi.acomi_backend.payment.api.dto.response.SpaceBillingSettingsResponse;
import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpaceBillingSettingsService {

    private final SpaceRepository spaceRepository;
    private final DashboardAccessService dashboardAccessService;

    @Transactional(readOnly = true)
    public SpaceBillingSettingsResponse getSettings(UUID spaceId, UUID callerId) {
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        return SpaceBillingSettingsResponse.from(loadSpace(spaceId));
    }

    @Transactional
    public SpaceBillingSettingsResponse updateSettings(
            UUID spaceId, UUID callerId, UpdateSpaceBillingSettingsRequest request) {
        dashboardAccessService.requireManagePayments(spaceId, callerId);
        SpaceEntity space = loadSpace(spaceId);

        boolean taxEnabled = request.isTaxEnabled();
        if (taxEnabled) {
            if (request.getTaxRatePercent() == null) {
                throw new BusinessException("Tax rate is required when tax is enabled", HttpStatus.BAD_REQUEST);
            }
            if (request.getPriceTaxMode() == null) {
                throw new BusinessException(
                        "Pricing mode (EXCLUSIVE or INCLUSIVE) is required when tax is enabled",
                        HttpStatus.BAD_REQUEST);
            }
            space.setTaxEnabled(true);
            space.setTaxRatePercent(request.getTaxRatePercent());
            space.setPriceTaxMode(request.getPriceTaxMode());
        } else {
            space.setTaxEnabled(false);
            space.setTaxRatePercent(null);
            space.setPriceTaxMode(null);
        }

        if (request.getGstin() != null) {
            String gstin = request.getGstin().trim();
            space.setGstin(gstin.isEmpty() ? null : gstin);
        }

        if (request.getBillingDueDay() != null) {
            space.setBillingDueDay(request.getBillingDueDay());
        } else if (space.getBillingDueDay() <= 0) {
            space.setBillingDueDay(1);
        }

        // Keep mode consistent when enabling without accidental null persist.
        if (space.isTaxEnabled() && space.getPriceTaxMode() == null) {
            space.setPriceTaxMode(PriceTaxMode.EXCLUSIVE);
        }
        if (space.isTaxEnabled() && space.getTaxRatePercent() != null
                && space.getTaxRatePercent().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Tax rate must be >= 0", HttpStatus.BAD_REQUEST);
        }

        spaceRepository.save(space);
        return SpaceBillingSettingsResponse.from(space);
    }

    private SpaceEntity loadSpace(UUID spaceId) {
        return spaceRepository
                .findById(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));
    }
}
