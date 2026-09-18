package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditWalletEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryWalletResponse {

    private UUID walletId;
    private UUID userId;
    private int availableCredits;
    private int lifetimeGranted;
    private int lifetimeUsed;
    private LocalDateTime updatedAt;

    public static InquiryWalletResponse from(InquiryCreditWalletEntity wallet) {
        return InquiryWalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .availableCredits(wallet.getAvailableCredits())
                .lifetimeGranted(wallet.getLifetimeGranted())
                .lifetimeUsed(wallet.getLifetimeUsed())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
