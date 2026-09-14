package com.acomi.acomi_backend.payment.infrastructure.delivery;

import com.acomi.acomi_backend.payment.application.dto.PaymentReminderMessage;
import com.acomi.acomi_backend.payment.application.port.out.MessageDeliveryProvider;
import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WhatsApp channel adapter.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code disabled} — no delivery</li>
 *   <li>{@code logging} — mock success for local/dev (no vendor HTTP)</li>
 *   <li>{@code live} — Meta Cloud API via {@link WhatsAppVendorClient}</li>
 * </ul>
 */
@Component
@Slf4j
public class WhatsAppMessageDeliveryProvider implements MessageDeliveryProvider {

    public static final String MODE_DISABLED = "disabled";
    public static final String MODE_LOGGING = "logging";
    public static final String MODE_LIVE = "live";

    private final WhatsAppReminderProperties properties;
    private final WhatsAppPhoneNormalizer phoneNormalizer;
    private final PaymentOverdueWhatsAppTemplateBuilder templateBuilder;
    private final WhatsAppVendorClient vendorClient;

    public WhatsAppMessageDeliveryProvider(
            WhatsAppReminderProperties properties,
            WhatsAppPhoneNormalizer phoneNormalizer,
            PaymentOverdueWhatsAppTemplateBuilder templateBuilder,
            WhatsAppVendorClient vendorClient) {
        this.properties = properties;
        this.phoneNormalizer = phoneNormalizer;
        this.templateBuilder = templateBuilder;
        this.vendorClient = vendorClient;
    }

    @Override
    public ReminderChannel channel() {
        return ReminderChannel.WHATSAPP;
    }

    @Override
    public boolean isConfigured() {
        String mode = properties.normalizedMode();
        if (MODE_LOGGING.equals(mode)) {
            return true;
        }
        if (MODE_LIVE.equals(mode)) {
            return properties.isLiveCredentialsPresent();
        }
        return false;
    }

    @Override
    public MessageDeliveryResult deliver(PaymentReminderMessage message) {
        String mode = properties.normalizedMode();

        if (MODE_DISABLED.equals(mode)) {
            log.info(
                    "PAYMENT_REMINDER_ATTEMPT mode=disabled payment={} space={} result=NOT_CONFIGURED",
                    message.getPaymentId(),
                    message.getSpaceId());
            return MessageDeliveryResult.unavailable("WHATSAPP_PROVIDER_NOT_CONFIGURED");
        }

        var normalized = phoneNormalizer.normalize(message.getRecipientMobile());
        if (normalized.isEmpty()) {
            log.warn(
                    "PAYMENT_REMINDER_FAILED payment={} member={} reason=INVALID_RECIPIENT",
                    message.getPaymentId(),
                    message.getMemberId());
            return MessageDeliveryResult.failed(
                    "INVALID_RECIPIENT", "Recipient mobile number is missing or invalid", false);
        }

        String masked = normalized.get().maskedForLog();
        String toDigits = normalized.get().digitsE164WithoutPlus();

        if (MODE_LOGGING.equals(mode)) {
            String mockId = "log-wa-" + UUID.randomUUID();
            log.info(
                    "PAYMENT_REMINDER_SENT mode=logging payment={} member={} phone={} daysOverdue={} "
                            + "outstanding={} mockMessageId={} preview={}",
                    message.getPaymentId(),
                    message.getMemberId(),
                    masked,
                    message.getDaysOverdue(),
                    message.getOutstandingAmount(),
                    mockId,
                    truncate(templateBuilder.previewText(message), 160));
            return MessageDeliveryResult.sent(mockId);
        }

        if (!MODE_LIVE.equals(mode)) {
            return MessageDeliveryResult.unavailable("WHATSAPP_PROVIDER_NOT_CONFIGURED");
        }

        if (!properties.isLiveCredentialsPresent()) {
            log.warn(
                    "PAYMENT_REMINDER_FAILED payment={} reason=PROVIDER_NOT_CONFIGURED mode=live",
                    message.getPaymentId());
            return MessageDeliveryResult.unavailable("WHATSAPP_PROVIDER_NOT_CONFIGURED");
        }

        WhatsAppTemplateSendRequest templateRequest = templateBuilder.build(message, toDigits);
        log.info(
                "PAYMENT_REMINDER_ATTEMPT mode=live payment={} member={} phone={} template={} lang={}",
                message.getPaymentId(),
                message.getMemberId(),
                masked,
                templateRequest.getTemplateName(),
                templateRequest.getLanguageCode());

        WhatsAppVendorClient.VendorSendResult vendorResult = vendorClient.sendTemplate(templateRequest);
        if (vendorResult.success()) {
            log.info(
                    "PAYMENT_REMINDER_SENT mode=live payment={} member={} phone={} providerMessageId={}",
                    message.getPaymentId(),
                    message.getMemberId(),
                    masked,
                    vendorResult.providerMessageId());
            return MessageDeliveryResult.sent(vendorResult.providerMessageId());
        }

        String code = vendorResult.failureCode() != null ? vendorResult.failureCode() : "PROVIDER_ERROR";
        log.warn(
                "PAYMENT_REMINDER_FAILED mode=live payment={} member={} phone={} failureCode={} retryable={}",
                message.getPaymentId(),
                message.getMemberId(),
                masked,
                code,
                vendorResult.retryable());
        if ("PROVIDER_NOT_CONFIGURED".equals(code)) {
            return MessageDeliveryResult.unavailable(code);
        }
        return MessageDeliveryResult.failed(code, vendorResult.failureReason(), vendorResult.retryable());
    }

    public String getMode() {
        return properties.normalizedMode();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
