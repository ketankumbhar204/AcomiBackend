package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.payment.domain.model.MonthlyBillingRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "monthly_billing_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBillingRunEntity extends BaseEntity {

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "billing_month", nullable = false)
    private LocalDate billingMonth;

    @Column(name = "business_timezone", nullable = false, length = 64)
    private String businessTimezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MonthlyBillingRunStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Builder.Default
    @Column(name = "eligible_accommodation", nullable = false)
    private int eligibleAccommodation = 0;

    @Builder.Default
    @Column(name = "eligible_mess", nullable = false)
    private int eligibleMess = 0;

    @Builder.Default
    @Column(name = "payments_created", nullable = false)
    private int paymentsCreated = 0;

    @Builder.Default
    @Column(name = "payments_skipped", nullable = false)
    private int paymentsSkipped = 0;

    @Builder.Default
    @Column(name = "payments_failed", nullable = false)
    private int paymentsFailed = 0;

    @Builder.Default
    @Column(name = "notifications_queued", nullable = false)
    private int notificationsQueued = 0;

    @Column(name = "skip_summary", length = 4000)
    private String skipSummary;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;
}
