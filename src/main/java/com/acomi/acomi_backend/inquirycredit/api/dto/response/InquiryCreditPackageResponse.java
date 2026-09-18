package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPackageEntity;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryCreditPackageResponse {

    private UUID id;
    private String name;
    private BigDecimal priceAmount;
    private String currency;
    private int credits;
    private boolean enabled;
    private int displayOrder;

    public static InquiryCreditPackageResponse from(InquiryCreditPackageEntity pkg) {
        return InquiryCreditPackageResponse.builder()
                .id(pkg.getId())
                .name(pkg.getName())
                .priceAmount(pkg.getPriceAmount())
                .currency(pkg.getCurrency())
                .credits(pkg.getCredits())
                .enabled(pkg.isEnabled())
                .displayOrder(pkg.getDisplayOrder())
                .build();
    }
}
