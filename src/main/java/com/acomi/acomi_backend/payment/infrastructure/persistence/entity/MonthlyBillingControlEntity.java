package com.acomi.acomi_backend.payment.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "monthly_billing_control")
@Getter
@Setter
@NoArgsConstructor
public class MonthlyBillingControlEntity {

    @Id
    private Short id;

    @Column(name = "enabled_from_month", nullable = false)
    private LocalDate enabledFromMonth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
