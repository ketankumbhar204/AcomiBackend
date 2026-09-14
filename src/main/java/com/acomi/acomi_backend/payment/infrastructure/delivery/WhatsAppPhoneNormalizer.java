package com.acomi.acomi_backend.payment.infrastructure.delivery;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Normalizes member mobile numbers for WhatsApp without mutating stored member data.
 * India-first: 10-digit national → {@code 91XXXXXXXXXX} (digits only, no plus — Meta Cloud API).
 */
@Component
public class WhatsAppPhoneNormalizer {

    private final WhatsAppReminderProperties properties;

    public WhatsAppPhoneNormalizer(WhatsAppReminderProperties properties) {
        this.properties = properties;
    }

    public record NormalizedPhone(String digitsE164WithoutPlus, String maskedForLog) {}

    public Optional<NormalizedPhone> normalize(String rawMobile) {
        if (!StringUtils.hasText(rawMobile)) {
            return Optional.empty();
        }
        String digits = rawMobile.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Optional.empty();
        }

        String defaultCc = properties.getDefaultCountryCode() == null
                ? "91"
                : properties.getDefaultCountryCode().replaceAll("\\D", "");
        if (!StringUtils.hasText(defaultCc)) {
            defaultCc = "91";
        }

        String e164;
        if (digits.length() == 10 && defaultCc.equals("91")) {
            // Indian mobile national format
            if (digits.charAt(0) < '6') {
                return Optional.empty();
            }
            e164 = defaultCc + digits;
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            e164 = digits;
        } else if (digits.length() >= 11 && digits.length() <= 15) {
            // Already international (without +)
            e164 = digits;
        } else {
            return Optional.empty();
        }

        return Optional.of(new NormalizedPhone(e164, mask(e164)));
    }

    public static String mask(String digits) {
        if (digits == null || digits.length() < 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }
}
