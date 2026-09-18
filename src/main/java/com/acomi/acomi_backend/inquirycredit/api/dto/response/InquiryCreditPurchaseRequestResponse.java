package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditPurchaseStatus;
import com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity.InquiryCreditPurchaseRequestEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InquiryCreditPurchaseRequestResponse {

    private UUID id;
    private UUID userId;
    private String userFullName;
    private String userMobileNumber;
    private UUID packageId;
    private BigDecimal amount;
    private String currency;
    private int credits;
    private String paymentMethod;
    private InquiryCreditPurchaseStatus status;
    private String utr;
    private LocalDateTime requestedAt;
    private LocalDateTime verifiedAt;
    private UUID verifiedByUserId;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static InquiryCreditPurchaseRequestResponse from(InquiryCreditPurchaseRequestEntity entity) {
        return from(entity, null);
    }

    public static InquiryCreditPurchaseRequestResponse from(
            InquiryCreditPurchaseRequestEntity entity,
            com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity user) {
        return InquiryCreditPurchaseRequestResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .userFullName(user != null ? user.getFullName() : null)
                .userMobileNumber(user != null ? user.getMobileNumber() : null)
                .packageId(entity.getPackageId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .credits(entity.getCredits())
                .paymentMethod(entity.getPaymentMethod())
                .status(entity.getStatus())
                .utr(entity.getUtr())
                .requestedAt(entity.getRequestedAt())
                .verifiedAt(entity.getVerifiedAt())
                .verifiedByUserId(entity.getVerifiedByUserId())
                .rejectionReason(entity.getRejectionReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
