package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.space.domain.model.PriceTaxMode;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Space-level recurring billing and tax configuration")
public class SpaceBillingSettingsResponse {

    private boolean taxEnabled;
    private BigDecimal taxRatePercent;
    private PriceTaxMode priceTaxMode;
    private String gstin;
    private int billingDueDay;

    public static SpaceBillingSettingsResponse from(SpaceEntity space) {
        return SpaceBillingSettingsResponse.builder()
                .taxEnabled(space.isTaxEnabled())
                .taxRatePercent(space.getTaxRatePercent())
                .priceTaxMode(space.getPriceTaxMode())
                .gstin(space.getGstin())
                .billingDueDay(space.getBillingDueDay() > 0 ? space.getBillingDueDay() : 1)
                .build();
    }
}
