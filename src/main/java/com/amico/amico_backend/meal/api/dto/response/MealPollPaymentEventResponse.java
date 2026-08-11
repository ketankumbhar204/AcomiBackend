package com.amico.amico_backend.meal.api.dto.response;

import com.amico.amico_backend.meal.domain.model.MealPollPaymentChoice;
import com.amico.amico_backend.meal.domain.model.MealPollPaymentEventType;
import com.amico.amico_backend.meal.domain.model.MealPollPaymentStatus;
import com.amico.amico_backend.meal.infrastructure.persistence.entity.MealPollPaymentEventEntity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MealPollPaymentEventResponse {

    private UUID eventId;
    private LocalDate pollDate;
    private MealPollPaymentEventType eventType;
    private MealPollPaymentStatus paymentStatus;
    private MealPollPaymentChoice paymentChoice;
    private BigDecimal amount;
    private String remarks;
    private UUID actorId;
    private LocalDateTime createdAt;

    public static MealPollPaymentEventResponse from(MealPollPaymentEventEntity entity) {
        return MealPollPaymentEventResponse.builder()
                .eventId(entity.getId())
                .pollDate(entity.getPollDate())
                .eventType(entity.getEventType())
                .paymentStatus(entity.getPaymentStatus())
                .paymentChoice(entity.getPaymentChoice())
                .amount(entity.getAmount())
                .remarks(entity.getRemarks())
                .actorId(entity.getActorId())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
