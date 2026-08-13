package com.acomi.acomi_backend.space.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class Space {

    private UUID id;
    private String name;
    private SpaceType type;
    private String address;
    private String contactNumber;
    private UUID ownerId;
    private boolean active;
    private BigDecimal defaultFoodCharge;
    private boolean foodIncludedInRent;
    private MealBillingType mealBillingType;
    private PrepaidBalanceUnit prepaidBalanceUnit;
    private boolean prepaidFallbackToPayPerMeal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
