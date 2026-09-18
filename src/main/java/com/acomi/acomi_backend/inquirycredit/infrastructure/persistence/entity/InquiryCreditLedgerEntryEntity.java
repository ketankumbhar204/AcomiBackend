package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity;

import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryCreditLedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Immutable ledger entry — the inquiry_credit_ledger table has no updated_at column,
 * so this entity does not extend BaseEntity.
 */
@Entity
@Table(
        name = "inquiry_credit_ledger",
        indexes = {
            @Index(
                    name = "idx_inquiry_credit_ledger_user_created",
                    columnList = "user_id, created_at DESC")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryCreditLedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 40)
    private InquiryCreditLedgerEntryType entryType;

    /** Positive = credit granted; negative = credit consumed. */
    @Column(name = "credits", nullable = false)
    private int credits;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
