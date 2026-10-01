package com.acomi.acomi_backend.inquirycredit.application.service;

/** Plain-text copy for inquiry-credit purchase emails. */
public final class InquiryCreditEmailCopy {

    private InquiryCreditEmailCopy() {}

    public static String pendingAdminSubject(int credits) {
        return "New inquiry credit payment to review (" + credits + " credits)";
    }

    public static String pendingAdminBody(int credits, String amountLabel) {
        return "Hello,\n\n"
                + "A seeker submitted a UPI payment screenshot request for "
                + credits
                + " inquiry credits ("
                + amountLabel
                + ").\n\n"
                + "Open ACOMI Admin → Credit payments to approve or reject. Approving adds the credits so they can send more enquiries.\n\n"
                + "Regards,\nACOMI\n";
    }

    public static String approvedSubject(int credits) {
        return credits + " inquiry credits have been added";
    }

    public static String approvedBody(String name, int credits) {
        return "Hello "
                + displayName(name)
                + ",\n\n"
                + credits
                + " inquiry credits have been added to your ACOMI account. You can continue sending email enquiries.\n\n"
                + "Regards,\nACOMI\n";
    }

    public static String rejectedSubject() {
        return "Inquiry credit payment was not approved";
    }

    public static String rejectedBody(String name, String reason) {
        String extra =
                reason != null && !reason.isBlank() ? " Reason: " + reason.trim() + "\n\n" : "\n\n";
        return "Hello "
                + displayName(name)
                + ",\n\n"
                + "Your inquiry credit payment request was not approved."
                + extra
                + "If you already paid, reply with the payment screenshot or submit a new request.\n\n"
                + "Regards,\nACOMI\n";
    }

    public static String amountLabel(java.math.BigDecimal amount, String currency) {
        if (amount == null) {
            return "";
        }
        String code = currency == null || currency.isBlank() ? "INR" : currency.trim();
        if ("INR".equalsIgnoreCase(code)) {
            return "₹" + amount.stripTrailingZeros().toPlainString();
        }
        return code + " " + amount.stripTrailingZeros().toPlainString();
    }

    private static String displayName(String name) {
        if (name == null || name.isBlank()) {
            return "there";
        }
        return name.trim();
    }
}
