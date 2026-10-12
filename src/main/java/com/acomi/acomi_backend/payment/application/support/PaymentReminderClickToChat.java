package com.acomi.acomi_backend.payment.application.support;

import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owner-reviewed WhatsApp click-to-chat copy. Does not send a message or change a payment.
 */
public final class PaymentReminderClickToChat {

    private static final DateTimeFormatter MONTH =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DUE =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private PaymentReminderClickToChat() {}

    public static String message(PaymentReminderMessage reminder, String targetLabel) {
        String name = text(reminder.getMemberName());
        String type = chargeLabel(reminder);
        String amount = formatAmount(reminder.getOutstandingAmount(), reminder.getCurrencyCode());
        String period = periodLabel(reminder.getMonth());
        String place = placeClause(reminder.getSpaceName(), targetLabel);
        boolean overdue = reminder.getDaysOverdue() > 0;

        StringBuilder body = new StringBuilder();
        body.append("Hi ");
        body.append(name != null ? name : "there");
        body.append(", this is a friendly reminder regarding your pending ");
        body.append(type);
        body.append(" payment");
        if (amount != null) {
            body.append(" of ").append(amount);
        }
        if (period != null) {
            body.append(" for ").append(period);
        }
        if (place != null) {
            body.append(" at ").append(place);
        }
        body.append(".\n\n");

        if (reminder.getDueDate() != null) {
            body.append("Due date: ").append(DUE.format(reminder.getDueDate())).append('\n');
        }
        if (amount != null) {
            body.append("Outstanding amount: ").append(amount).append('\n');
        }
        body.append("Payment status: ").append(overdue ? "Overdue" : "Pending");
        if (overdue) {
            body.append('\n').append("Days overdue: ").append(reminder.getDaysOverdue());
        }
        body.append("\n\nPlease arrange the payment at your earliest convenience. ");
        body.append("If you have already paid, kindly share the payment confirmation ");
        body.append("or UTR/reference number so we can update our records.");
        String space = text(reminder.getSpaceName());
        if (space != null) {
            body.append("\n\nThank you,\n").append(space);
        } else {
            body.append("\n\nThank you");
        }
        return body.toString();
    }

    public static String inAppMessage(PaymentReminderMessage reminder, String targetLabel) {
        String type = chargeLabel(reminder);
        String amount = formatAmount(reminder.getOutstandingAmount(), reminder.getCurrencyCode());
        String period = periodLabel(reminder.getMonth());
        String place = placeClause(reminder.getSpaceName(), targetLabel);
        boolean overdue = reminder.getDaysOverdue() > 0;

        StringBuilder body = new StringBuilder();
        body.append(type);
        if (amount != null) {
            body.append(" of ").append(amount);
        }
        if (period != null) {
            body.append(" for ").append(period);
        }
        body.append(overdue ? " is overdue" : " is pending");
        if (place != null) {
            body.append(" at ").append(place);
        }
        body.append('.');
        if (reminder.getDueDate() != null) {
            body.append(" Due ").append(DUE.format(reminder.getDueDate())).append('.');
        }
        return body.toString();
    }

    public static String waMeUrl(String internationalDigits, String message) {
        if (internationalDigits == null || internationalDigits.isBlank() || message == null || message.isBlank()) {
            return null;
        }
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + internationalDigits + "?text=" + encoded;
    }

    private static String chargeLabel(PaymentReminderMessage reminder) {
        String period = periodLabel(reminder.getMonth());
        String title = text(reminder.getTitle());
        if (title != null && (period == null || !title.toLowerCase(Locale.ENGLISH).contains(period.toLowerCase(Locale.ENGLISH)))) {
            return title;
        }
        return humanType(reminder.getPaymentType());
    }

    private static String humanType(SpacePaymentType type) {
        if (type == null) {
            return "payment";
        }
        return switch (type) {
            case RENT -> "Rent";
            case MEAL -> "Meal";
            case DEPOSIT -> "Deposit";
            case MAINTENANCE -> "Maintenance";
            default -> "payment";
        };
    }

    private static String periodLabel(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return MONTH.format(YearMonth.parse(month.trim()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String placeClause(String spaceName, String targetLabel) {
        List<String> parts = new ArrayList<>();
        String space = text(spaceName);
        if (space != null) {
            parts.add(space);
        }
        if (targetLabel != null) {
            for (String raw : targetLabel.split("[·|]")) {
                String part = text(raw);
                if (part == null) {
                    continue;
                }
                if (space != null && part.equalsIgnoreCase(space)) {
                    continue;
                }
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) {
            return null;
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        format.setGroupingUsed(true);
        String value = format.format(amount);
        if (currency == null || currency.isBlank() || "INR".equalsIgnoreCase(currency.trim())) {
            return "₹" + value;
        }
        return currency.trim() + " " + value;
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replaceAll("[\\r\\n\\t]+", " ");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
