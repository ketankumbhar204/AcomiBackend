package com.acomi.acomi_backend.payment.infrastructure.delivery;

import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds WhatsApp template body parameters for overdue payment reminders.
 * Keeps copy construction out of Web/Mobile and out of billing services.
 *
 * <p>Expected Meta utility template body placeholders (order-sensitive):
 * <ol>
 *   <li>member name</li>
 *   <li>payment type label</li>
 *   <li>space name</li>
 *   <li>outstanding amount</li>
 *   <li>due date</li>
 *   <li>days overdue</li>
 * </ol>
 * Align approved Meta template variable order with this list before going live.
 */
@Component
public class PaymentOverdueWhatsAppTemplateBuilder {

    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final WhatsAppReminderProperties properties;

    public PaymentOverdueWhatsAppTemplateBuilder(WhatsAppReminderProperties properties) {
        this.properties = properties;
    }

    public WhatsAppTemplateSendRequest build(PaymentReminderMessage message, String toDigits) {
        List<String> body = new ArrayList<>();
        body.add(safe(message.getMemberName(), "Member"));
        body.add(paymentTypeLabel(message));
        body.add(safe(message.getSpaceName(), "Space"));
        body.add(formatAmount(message.getOutstandingAmount(), message.getCurrencyCode()));
        body.add(message.getDueDate() != null ? DUE_DATE.format(message.getDueDate()) : "-");
        body.add(String.valueOf(Math.max(0, message.getDaysOverdue())));

        String template = properties.getPaymentOverdueTemplate();
        String language = properties.getTemplateLanguage() == null || properties.getTemplateLanguage().isBlank()
                ? "en"
                : properties.getTemplateLanguage().trim();

        return WhatsAppTemplateSendRequest.builder()
                .toDigits(toDigits)
                .templateName(template)
                .languageCode(language)
                .bodyParameters(body)
                .build();
    }

    /** Human-readable preview used by logging mode (not sent to Meta). */
    public String previewText(PaymentReminderMessage message) {
        return "Hi "
                + safe(message.getMemberName(), "Member")
                + ", your "
                + paymentTypeLabel(message)
                + " payment for "
                + safe(message.getSpaceName(), "Space")
                + " is overdue. Amount due: "
                + formatAmount(message.getOutstandingAmount(), message.getCurrencyCode())
                + ". Due date: "
                + (message.getDueDate() != null ? DUE_DATE.format(message.getDueDate()) : "-")
                + ". Days overdue: "
                + Math.max(0, message.getDaysOverdue())
                + ".";
    }

    private static String paymentTypeLabel(PaymentReminderMessage message) {
        if (message.getPaymentType() == null) {
            return "payment";
        }
        return switch (message.getPaymentType()) {
            case RENT -> "rent";
            case MEAL -> "meal";
            case DEPOSIT -> "deposit";
            case MAINTENANCE -> "maintenance";
            default -> "payment";
        };
    }

    private static String formatAmount(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        String code = currency == null || currency.isBlank() ? "INR" : currency.trim();
        if ("INR".equalsIgnoreCase(code)) {
            return "₹" + value.toPlainString();
        }
        return code + " " + value.toPlainString();
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        // Meta templates reject newlines/tabs in parameters
        return value.trim().replaceAll("[\\r\\n\\t]+", " ");
    }
}
