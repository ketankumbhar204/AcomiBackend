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
@Table(name = "inquiry_payment_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryPaymentConfigEntity extends BaseEntity {

    @Column(name = "upi_id", length = 120)
    private String upiId;

    @Column(name = "qr_file_id")
    private UUID qrFileId;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /** Free WEB (email) enquiries per calendar day before paid credits apply. */
    @Column(name = "web_free_daily_limit", nullable = false)
    @Builder.Default
    private int webFreeDailyLimit = 5;

    /**
     * ANDROID billing: {@code FREE} (rate-limit only) or {@code CREDITS}
     * (daily free then wallet).
     */
    @Column(name = "android_billing_mode", nullable = false, length = 20)
    @Builder.Default
    private String androidBillingMode = "FREE";

    /** Free ANDROID enquiries per day when billing mode is CREDITS. */
    @Column(name = "android_free_daily_limit", nullable = false)
    @Builder.Default
    private int androidFreeDailyLimit = 5;

    /** Max ANDROID enquiries per rolling hour (abuse protection). */
    @Column(name = "android_hourly_rate_limit", nullable = false)
    @Builder.Default
    private int androidHourlyRateLimit = 20;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;
}
