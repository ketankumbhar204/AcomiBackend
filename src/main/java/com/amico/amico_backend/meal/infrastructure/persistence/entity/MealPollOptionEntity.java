package com.amico.amico_backend.meal.infrastructure.persistence.entity;

import com.amico.amico_backend.common.model.BaseEntity;
import com.amico.amico_backend.meal.domain.model.MealPollOptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meal_poll_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPollOptionEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poll_id", nullable = false)
    private MealPollEntity poll;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 20)
    private MealPollOptionType optionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_menu_entry_id")
    private DailyMenuEntryEntity dailyMenuEntry;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 200)
    private String label;

    private String detail;
}
