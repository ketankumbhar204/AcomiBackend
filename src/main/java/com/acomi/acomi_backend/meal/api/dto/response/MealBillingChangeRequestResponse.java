package com.acomi.acomi_backend.meal.api.dto.response;

import com.acomi.acomi_backend.meal.domain.model.MealBillingChangeRequestStatus;
import com.acomi.acomi_backend.meal.infrastructure.persistence.entity.MealBillingChangeRequestEntity;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealBillingChangeRequestResponse {

    private UUID requestId;
    private UUID memberId;
    private String memberName;
    private MealBillingType requestedBillingType;
    private MealBillingChangeRequestStatus status;
    private String customerNotes;
    private String ownerNotes;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public static MealBillingChangeRequestResponse from(MealBillingChangeRequestEntity entity) {
        return MealBillingChangeRequestResponse.builder()
                .requestId(entity.getId())
                .memberId(entity.getMember().getId())
                .memberName(entity.getMember().getFullName())
                .requestedBillingType(entity.getRequestedBillingType())
                .status(entity.getStatus())
                .customerNotes(entity.getCustomerNotes())
                .ownerNotes(entity.getOwnerNotes())
                .createdAt(entity.getCreatedAt())
                .resolvedAt(entity.getResolvedAt())
                .build();
    }
}
