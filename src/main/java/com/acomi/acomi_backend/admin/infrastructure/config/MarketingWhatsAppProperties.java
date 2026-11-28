package com.acomi.acomi_backend.admin.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * WhatsApp Business Platform settings for automatic listing marketing sends.
 * Secrets come from environment variables — never commit them or expose them to clients.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.marketing.whatsapp")
public class MarketingWhatsAppProperties {

    /** Master switch. Default false. */
    private boolean enabled = false;

    /** {@code disabled} | {@code logging} | {@code live}. */
    private String mode = "disabled";

    /**
     * Set true only after Meta media upload + approved promotional template mapping are implemented
     * and end-to-end tested. Keeps automatic mode unavailable until then.
     */
    private boolean senderReady = false;

    private String accessToken = "";

    private String phoneNumberId = "";

    private String businessAccountId = "";

    /** Approved Meta template name for promotional listing messages. */
    private String promotionalTemplate = "";

    private String templateLanguage = "en";

    private String defaultCountryCode = "91";

    /** Reject duplicate automatic submissions for the same listing within this window. */
    private int duplicateWindowSeconds = 120;

    public String normalizedMode() {
        return mode == null ? "disabled" : mode.trim().toLowerCase();
    }

    public boolean hasLiveCredentials() {
        return StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId);
    }

    public boolean hasApprovedPromotionalTemplate() {
        return StringUtils.hasText(promotionalTemplate);
    }

    public List<String> unavailableReasons() {
        List<String> reasons = new ArrayList<>();
        if (!enabled) {
            reasons.add("INTEGRATION_DISABLED");
        }
        if ("disabled".equals(normalizedMode())) {
            reasons.add("MODE_DISABLED");
        }
        if (!hasLiveCredentials()) {
            reasons.add("CREDENTIALS_MISSING");
        }
        if (!hasApprovedPromotionalTemplate()) {
            reasons.add("TEMPLATE_MISSING");
        }
        if (!senderReady) {
            reasons.add("SENDER_NOT_READY");
        }
        return reasons;
    }

    public boolean isAutomaticSendAvailable() {
        return unavailableReasons().isEmpty();
    }

    public String maskedBusinessNumberHint() {
        if (!StringUtils.hasText(phoneNumberId)) {
            return null;
        }
        String id = phoneNumberId.trim();
        if (id.length() <= 4) {
            return "••••";
        }
        return "••••" + id.substring(id.length() - 4);
    }
}
