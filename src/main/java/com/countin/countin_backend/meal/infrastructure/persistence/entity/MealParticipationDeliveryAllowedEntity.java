package com.countin.countin_backend.meal.infrastructure.persistence.entity;

import com.countin.countin_backend.common.model.BaseEntity;
import com.countin.countin_backend.meal.domain.model.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "meal_participation_delivery_allowed",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_meal_part_delivery_allowed_part_meal_loc",
                        columnNames = {"participation_id", "meal_type", "delivery_location_id"}),
        indexes = {
            @Index(name = "idx_meal_part_delivery_allowed_participation", columnList = "participation_id"),
            @Index(name = "idx_meal_part_delivery_allowed_part_meal", columnList = "participation_id, meal_type")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealParticipationDeliveryAllowedEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id", nullable = false)
    private MealParticipationEntity participation;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_location_id", nullable = false)
    private MealDeliveryLocationEntity deliveryLocation;
}
