package com.acomi.acomi_backend.meal.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.reference.BusinessReferencePrefix;
import com.acomi.acomi_backend.common.reference.BusinessReferenced;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.meal.domain.model.FoodScope;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "food_categories",
        indexes = {
            @Index(name = "idx_food_categories_space_id", columnList = "space_id"),
            @Index(name = "idx_food_categories_owner_id", columnList = "owner_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodCategoryEntity extends BaseEntity implements BusinessReferenced {
    @Column(name = "reference_code", nullable = false, updatable = false, length = 24)
    private String referenceCode;

    @Override
    public BusinessReferencePrefix businessReferencePrefix() {
        return BusinessReferencePrefix.FDC;
    }


    @Column(nullable = false, length = 100)
    private String name;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FoodScope scope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private SpaceEntity space;

    /** Owner of the space that created a custom category. Null for global categories. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
