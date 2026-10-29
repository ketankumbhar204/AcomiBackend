package com.acomi.acomi_backend.servicecontact.domain;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.servicecontact.domain.model.ServiceContactCategory;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared validation and normalization for service contacts. */
public final class ServiceContactRules {

    public static final int NAME_MAX = 150;
    public static final int CUSTOM_CATEGORY_MAX = 80;
    public static final int DESCRIPTION_MAX = 500;
    public static final int ADDRESS_MAX = 300;
    public static final int NOTES_MAX = 2000;
    public static final int EMAIL_MAX = 254;

    private static final Pattern CUSTOM_CATEGORY =
            Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} .&/'\\-]{1,79}$");
    private static final Pattern EMAIL =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private ServiceContactRules() {}

    public static String requireBusinessName(String raw) {
        String name = collapse(raw);
        if (name == null) {
            throw new BusinessException("INVALID_NAME", "Contact name is required");
        }
        if (name.length() > NAME_MAX) {
            throw new BusinessException("INVALID_NAME", "Contact name is too long");
        }
        return name;
    }

    public static String normalizeComparableName(String raw) {
        String name = collapse(raw);
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    public static ServiceContactCategory requireCategory(String raw) {
        return ServiceContactCategory.parse(raw);
    }

    /**
     * Custom labels are stored only for {@link ServiceContactCategory#OTHER}.
     * Predefined categories must not carry a parallel custom name.
     */
    public static String customCategoryName(ServiceContactCategory category, String raw) {
        String custom = collapse(raw);
        if (category == ServiceContactCategory.OTHER) {
            if (custom == null) {
                throw new BusinessException("INVALID_CATEGORY", "Enter a name for the custom category");
            }
            if (custom.length() > CUSTOM_CATEGORY_MAX || !CUSTOM_CATEGORY.matcher(custom).matches()) {
                throw new BusinessException(
                        "INVALID_CATEGORY",
                        "Custom category must be 2–80 letters, numbers, or simple punctuation");
            }
            return custom;
        }
        if (custom != null) {
            throw new BusinessException(
                    "INVALID_CATEGORY", "Custom category is only used when category is Other");
        }
        return null;
    }

    public static String optionalText(String raw, int max, String code, String label) {
        String value = collapse(raw);
        if (value == null) {
            return null;
        }
        if (value.length() > max) {
            throw new BusinessException(code, label + " is too long");
        }
        return value;
    }

    public static String optionalEmail(String raw) {
        String value = raw == null ? null : raw.trim();
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() > EMAIL_MAX || !EMAIL.matcher(value).matches()) {
            throw new BusinessException("INVALID_EMAIL", "Email address is not valid");
        }
        return value;
    }

    public static String optionalPhone(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MobileNumberNormalizer.normalize(raw);
        } catch (BusinessException ex) {
            throw new BusinessException("INVALID_PHONE", label + " must be a valid mobile number");
        }
    }

    public static void requireContactMethod(String primary, String alternate, String whatsapp, String email) {
        if (primary == null && alternate == null && whatsapp == null && email == null) {
            throw new BusinessException(
                    "CONTACT_METHOD_REQUIRED", "Add a phone number, WhatsApp number, or email");
        }
    }

    public static Set<String> phoneSet(String primary, String alternate, String whatsapp) {
        Set<String> phones = new LinkedHashSet<>();
        if (primary != null) {
            phones.add(primary);
        }
        if (alternate != null) {
            phones.add(alternate);
        }
        if (whatsapp != null) {
            phones.add(whatsapp);
        }
        return phones;
    }

    public static String displayPhone(String national) {
        return MobileNumberNormalizer.formatWithCountryCode(national);
    }

    public static String likePattern(String raw) {
        String collapsed = collapse(raw);
        if (collapsed == null) {
            return null;
        }
        return "%" + collapsed.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    public static String digits(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    private static String collapse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replaceAll("\\s+", " ");
        return value.isEmpty() ? null : value;
    }
}
