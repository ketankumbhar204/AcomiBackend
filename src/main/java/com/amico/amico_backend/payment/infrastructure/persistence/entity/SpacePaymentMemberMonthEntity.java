package com.amico.amico_backend.payment.infrastructure.persistence.entity;

import com.amico.amico_backend.common.model.BaseEntity;
import com.amico.amico_backend.dashboard.domain.model.MemberPaymentStatus;
import com.amico.amico_backend.space.domain.model.MealBillingType;
import com.amico.amico_backend.space.domain.model.PrepaidBalanceUnit;
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
        name = "space_payment_member_month",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_space_payment_member_month",
                        columnNames = {"space_id", "month", "member_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpacePaymentMemberMonthEntity extends BaseEntity {

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "month", nullable = false, length = 7)
    private String month;

    @Column(name = "member_name", nullable = false, length = 200)
    private String memberName;

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
    @Column(name = "status", nullable = false, length = 30)
    private MemberPaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_billing_type", length = 30)
    private MealBillingType mealBillingType;

    @Column(name = "meal_balance_remaining", precision = 12, scale = 2)
    private BigDecimal mealBalanceRemaining;

    @Column(name = "meal_balance_purchased", precision = 12, scale = 2)
    private BigDecimal mealBalancePurchased;

    @Column(name = "meal_balance_consumed", precision = 12, scale = 2)
    private BigDecimal mealBalanceConsumed;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_balance_unit", length = 20)
    private PrepaidBalanceUnit mealBalanceUnit;
}
