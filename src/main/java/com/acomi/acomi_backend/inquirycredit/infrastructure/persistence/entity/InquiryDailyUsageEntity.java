package com.acomi.acomi_backend.inquirycredit.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.inquirycredit.domain.model.InquiryClientChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "inquiry_daily_usage",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_inquiry_daily_usage_user_date_channel",
                    columnNames = {"user_id", "usage_date", "channel"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryDailyUsageEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private InquiryClientChannel channel;

    @Column(name = "free_used", nullable = false)
    @Builder.Default
    private int freeUsed = 0;
}
