package com.acomi.acomi_backend.payment.api.dto.response;

import com.acomi.acomi_backend.payment.domain.model.ReminderChannel;
import com.acomi.acomi_backend.payment.domain.model.ReminderDeliveryStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentReminderDeliveryResponse {

    private UUID deliveryId;
    private UUID paymentId;
    private ReminderChannel channel;
    private ReminderDeliveryStatus deliveryStatus;
    private LocalDate businessDate;
    private String providerMessageId;
    private String failureReason;
    private String failureCode;
    private boolean providerConfigured;
    private boolean retryable;
    private LocalDateTime sentAt;
    private LocalDateTime lastAttemptAt;
    private int attemptCount;
    /** Click-to-chat text. Present only for an owner-prepared reminder. Null means nothing was opened. */
    private String messageText;
    /** https://wa.me link. Null when the member phone cannot be used. */
    private String whatsAppUrl;
    /** Digits only, no plus sign. */
    private String internationalPhone;
    /** True when an in-app notification exists for the member. Does not mean a push was delivered. */
    private boolean inAppRecorded;
    private String inAppFailureCode;
}
