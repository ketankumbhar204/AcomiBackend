package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.dashboard.domain.model.DashboardFinancialSource;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import com.acomi.acomi_backend.space.domain.model.PrepaidBalanceUnit;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "space_payment_month_summary",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_space_payment_month_summary",
                        columnNames = {"space_id", "month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpacePaymentMonthSummaryEntity extends BaseEntity {

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Enumerated(EnumType.STRING)
    @Column(name = "space_type", nullable = false, length = 30)
    private SpaceType spaceType;

    @Column(name = "expected_charges", precision = 12, scale = 2)
    private BigDecimal expectedCharges;

    @Column(name = "collected", precision = 12, scale = 2)
    private BigDecimal collected;

    @Column(name = "under_review", precision = 12, scale = 2)
    private BigDecimal underReview;

    @Column(name = "pending", precision = 12, scale = 2)
    private BigDecimal pending;

    @Builder.Default
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_source", length = 30)
    private DashboardFinancialSource financialSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_billing_type", length = 30)
    private MealBillingType mealBillingType;

    @Column(name = "mixed_meal_billing")
    private Boolean mixedMealBilling;

    @Column(name = "prepaid_meals_remaining", precision = 12, scale = 2)
    private BigDecimal prepaidMealsRemaining;

    @Column(name = "prepaid_amount_collected", precision = 12, scale = 2)
    private BigDecimal prepaidAmountCollected;

    @Column(name = "prepaid_currency_code", length = 3)
    private String prepaidCurrencyCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "prepaid_unit", length = 20)
    private PrepaidBalanceUnit prepaidUnit;

    @Builder.Default
    @Column(name = "pending_members", nullable = false)
    private int pendingMembers = 0;

    @Builder.Default
    @Column(name = "member_count", nullable = false)
    private int memberCount = 0;
}
