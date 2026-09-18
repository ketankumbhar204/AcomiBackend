package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inquiry_credit_wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryCreditWalletEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "available_credits", nullable = false)
    @Builder.Default
    private int availableCredits = 0;

    @Column(name = "lifetime_granted", nullable = false)
    @Builder.Default
    private int lifetimeGranted = 0;

    @Column(name = "lifetime_used", nullable = false)
    @Builder.Default
    private int lifetimeUsed = 0;
}
