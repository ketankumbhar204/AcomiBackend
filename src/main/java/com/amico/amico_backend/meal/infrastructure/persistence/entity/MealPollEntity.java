package com.amico.amico_backend.meal.infrastructure.persistence.entity;

import com.amico.amico_backend.common.model.BaseEntity;
import com.amico.amico_backend.meal.domain.model.MealPollCloseSource;
import com.amico.amico_backend.meal.domain.model.MealPollStatus;
import com.amico.amico_backend.meal.domain.model.MealType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
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
        name = "meal_polls",
        indexes = {@Index(name = "idx_meal_polls_space_date", columnList = "space_id, poll_date")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPollEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private SpaceEntity space;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_menu_id", nullable = false)
    private DailyMenuEntity dailyMenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Column(name = "poll_date", nullable = false)
    private LocalDate pollDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MealPollStatus status;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Wall-clock close deadline in the space timezone. */
    @Column(name = "poll_close_at")
    private LocalDateTime pollCloseAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_source", length = 20)
    private MealPollCloseSource closeSource;
}
