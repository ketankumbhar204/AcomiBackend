package com.acomi.acomi_backend.meal.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.reference.BusinessReferencePrefix;
import com.acomi.acomi_backend.common.reference.BusinessReferenced;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.meal.domain.model.FoodType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "meal_combos",
        indexes = {
            @Index(name = "idx_meal_combos_space_id", columnList = "space_id"),
            @Index(name = "idx_meal_combos_owner_id", columnList = "owner_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealComboEntity extends BaseEntity implements BusinessReferenced {
    @Column(name = "reference_code", nullable = false, updatable = false, length = 24)
    private String referenceCode;

    @Override
    public BusinessReferencePrefix businessReferencePrefix() {
        return BusinessReferencePrefix.CMB;
    }


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    /** Owner whose spaces share this combo. The space column records where it was created. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    /** Set only for seeded sample combos so later spaces do not insert another copy. */
    @Column(name = "sample_key", length = 40)
    private String sampleKey;

    /** False for extra exact sample copies that must stay for existing menu references. */
    @Builder.Default
    @Column(name = "library_listed", nullable = false)
    private boolean libraryListed = true;

    @Column(nullable = false, length = 100)
    private String name;

    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "food_type", nullable = false, length = 20)
    private FoodType foodType = FoodType.VEG;

    @Column(name = "photo_file_id")
    private java.util.UUID photoFileId;
}
