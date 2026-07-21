package com.countin.countin_backend.meal.infrastructure.persistence.entity;

import com.countin.countin_backend.common.model.BaseEntity;
import com.countin.countin_backend.meal.domain.model.DailyMenuStatus;
import com.countin.countin_backend.meal.domain.model.MealType;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "daily_menus",
        indexes = {@Index(name = "idx_daily_menus_space_date", columnList = "space_id, menu_date")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyMenuEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @Column(name = "menu_date", nullable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DailyMenuStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * JSON snapshot of notes + options as last shared with customers.
     * Used while status is MODIFIED so live edits are not customer-visible yet.
     */
    @Column(name = "published_snapshot", columnDefinition = "TEXT")
    private String publishedSnapshot;

    private String notes;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;
}
