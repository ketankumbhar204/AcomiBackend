package com.acomi.acomi_backend.payment.infrastructure.delivery;

import com.acomi.acomi_backend.notification.application.service.DeviceTokenService;
import com.acomi.acomi_backend.notification.config.PushProperties;
import com.acomi.acomi_backend.notification.infrastructure.firebase.FirebaseMessagingService;
import com.acomi.acomi_backend.notification.infrastructure.persistence.entity.UserDeviceTokenEntity;
import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import com.acomi.acomi_backend.payment.application.port.out.MessageDeliveryProvider;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Payment reminder transport while WhatsApp is disabled. Sends only to the member's
 * registered devices and reports provider acceptance, not that the notification was seen.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmPaymentReminderDeliveryProvider implements MessageDeliveryProvider {

    private static final DateTimeFormatter DUE_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final PushProperties pushProperties;
    private final FirebaseMessagingService firebaseMessagingService;
    private final DeviceTokenService deviceTokenService;

    @Override
    public ReminderChannel channel() {
        return ReminderChannel.PUSH;
    }

    @Override
    public boolean isConfigured() {
        return pushProperties.isEnabled() && firebaseMessagingService.isAvailable();
    }

    @Override
    public MessageDeliveryResult deliver(PaymentReminderMessage message) {
        if (message.getRecipientUserId() == null) {
            log.info(
                    "Payment reminder skipped channel=FCM stage=recipient payment={} reason=no_acomi_account fcmAttempted=false",
                    message.getPaymentId());
            return MessageDeliveryResult.failed(
                    "MEMBER_NOT_ON_ACOMI",
                    "Member does not have an ACOMI account for push notifications",
                    false);
        }
        if (!isConfigured()) {
            log.info(
                    "Payment reminder skipped channel=FCM stage=provider payment={} reason=fcm_unavailable fcmAttempted=false",
                    message.getPaymentId());
            return MessageDeliveryResult.failed(
                    "FCM_UNAVAILABLE",
                    "Firebase push notifications are not available",
                    true);
        }
        List<UserDeviceTokenEntity> devices = deviceTokenService.listActiveForUser(message.getRecipientUserId());
        if (devices.isEmpty()) {
            log.info(
                    "Payment reminder skipped channel=FCM stage=registration payment={} reason=no_active_token fcmAttempted=false",
                    message.getPaymentId());
            return MessageDeliveryResult.failed(
                    "NO_ACTIVE_DEVICE_TOKEN",
                    "Member has no registered device for push notifications",
                    true);
        }
        log.info(
                "Payment reminder FCM attempt stage=send payment={} registrations={}",
                message.getPaymentId(),
                devices.size());
        String title = message.getDaysOverdue() > 0 ? "Payment Overdue" : "Payment Reminder";
        String body = body(message);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "PAYMENT_REMINDER_SENT");
        data.put("actionRoute", "PaymentDetail");
        if (message.getPaymentId() != null) {
            data.put("entityId", message.getPaymentId().toString());
            data.put("entityType", "PAYMENT");
        }
        if (message.getSpaceId() != null) {
            data.put("spaceId", message.getSpaceId().toString());
        }
        List<String> tokens = devices.stream().map(UserDeviceTokenEntity::getFcmToken).toList();
        FirebaseMessagingService.SendOutcome outcome =
                firebaseMessagingService.sendOutcome(title, body, data, tokens);
        if (!outcome.invalidTokens().isEmpty()) {
            deviceTokenService.deactivateTokens(outcome.invalidTokens());
        }
        if (outcome.accepted() > 0) {
            log.info(
                    "Payment reminder accepted by FCM payment={} user={} devices={} invalid={}",
                    message.getPaymentId(),
                    message.getRecipientUserId(),
                    outcome.accepted(),
                    outcome.invalidTokens().size());
            return MessageDeliveryResult.sent("fcm-accepted:" + outcome.accepted());
        }
        if (outcome.transportFailed()) {
            return MessageDeliveryResult.failed(
                    "FCM_UNAVAILABLE",
                    "Firebase push notifications are not available",
                    true);
        }
        return MessageDeliveryResult.failed(
                "FCM_REJECTED",
                "Firebase did not accept the payment reminder",
                true);
    }

    private static String body(PaymentReminderMessage message) {
        String amount = formatAmount(message.getOutstandingAmount(), message.getCurrencyCode());
        String description = message.getTitle() != null && !message.getTitle().isBlank()
                ? message.getTitle().trim()
                : paymentLabel(message.getPaymentType());
        if (message.getDaysOverdue() > 0) {
            return "Your payment of " + amount + " for " + description
                    + " is overdue. Please open ACOMI to review and complete the payment.";
        }
        String due = message.getDueDate() != null ? message.getDueDate().format(DUE_DATE) : "the due date";
        return "Your payment of " + amount + " for " + description
                + " is due on " + due + ". Please open ACOMI to view the payment details.";
    }

    private static String formatAmount(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        String text = format.format(value);
        if (currency == null || currency.isBlank() || "INR".equalsIgnoreCase(currency.trim())) {
            return "₹" + text;
        }
        return currency.trim() + " " + text;
    }

    private static String paymentLabel(SpacePaymentType type) {
        if (type == null) {
            return "payment";
        }
        return switch (type) {
            case RENT -> "rent";
            case DEPOSIT -> "security deposit";
            case MEAL -> "meal";
            default -> "payment";
        };
    }
}
