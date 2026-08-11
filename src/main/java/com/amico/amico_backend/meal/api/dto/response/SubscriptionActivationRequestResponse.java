package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.SubscriptionActivationRequestStatus;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.SubscriptionActivationRequestEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SubscriptionActivationRequestResponse {

    private UUID requestId;
    private UUID memberId;
    private String memberName;
    private UUID planId;
    private String planName;
    private SubscriptionActivationRequestStatus status;
    private String paymentReference;
    private String paymentProofImageUrl;
    private String customerNotes;
    private String ownerNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public static SubscriptionActivationRequestResponse from(SubscriptionActivationRequestEntity entity) {
        return SubscriptionActivationRequestResponse.builder()
                .requestId(entity.getId())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .planId(entity.getPlan().getId())
                .planName(entity.getPlan().getName())
                .status(entity.getStatus())
                .paymentReference(entity.getPaymentReference())
                .paymentProofImageUrl(entity.getPaymentProofImageUrl())
                .customerNotes(entity.getCustomerNotes())
                .ownerNotes(entity.getOwnerNotes())
                .createdAt(entity.getCreatedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}
