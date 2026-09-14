package com.acomi.acomi_backend.payment.infrastructure.delivery;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WhatsApp reminder delivery settings. Secrets come from environment variables — never commit them.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.reminders.whatsapp")
public class WhatsAppReminderProperties {

    /**
     * {@code disabled} | {@code logging} | {@code live}.
     * Default {@code disabled} — live sending requires explicit configuration.
     */
    private String mode = "disabled";

    /** Vendor adapter: {@code meta_cloud} (default). Reserved for future BSPs. */
    private String provider = "meta_cloud";

    private String baseUrl = "https://graph.facebook.com";

    private String apiVersion = "v21.0";

    /** Meta permanent / system-user access token. Env: ACOMI_WHATSAPP_ACCESS_TOKEN */
    private String accessToken = "";

    /** Meta WhatsApp phone number ID. Env: ACOMI_WHATSAPP_PHONE_NUMBER_ID */
    private String phoneNumberId = "";

    /** Optional WABA id (ops/audit only). Env: ACOMI_WHATSAPP_WABA_ID */
    private String businessAccountId = "";

    /**
     * Approved Meta template name for overdue payment reminders.
     * Env: ACOMI_WHATSAPP_TEMPLATE_PAYMENT_OVERDUE
     */
    private String paymentOverdueTemplate = "";

    /** Template language code (e.g. en, hi, mr). */
    private String templateLanguage = "en";

    /** Default country calling code when national numbers are stored (India = 91). */
    private String defaultCountryCode = "91";

    private int connectTimeoutMs = 5_000;

    private int readTimeoutMs = 15_000;

    /** Max delivery attempts per payment/business-day for transient failures. */
    private int maxTransientAttempts = 3;

    public String normalizedMode() {
        return mode == null ? "disabled" : mode.trim().toLowerCase();
    }

    public boolean isLiveCredentialsPresent() {
        return accessToken != null
                && !accessToken.isBlank()
                && phoneNumberId != null
                && !phoneNumberId.isBlank()
                && paymentOverdueTemplate != null
                && !paymentOverdueTemplate.isBlank();
    }
}
