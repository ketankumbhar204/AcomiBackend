package com.acomi.acomi_backend.common.exception;

import com.acomi.acomi_backend.enquiry.domain.model.EnquiryDeliveryChannel;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Same-channel contact delivery is still active (not expired). Cross-channel is not an error.
 */
public class AlreadyDeliveredException extends BusinessException {

    public static final String ERROR_CODE = "ALREADY_DELIVERED";

    private final EnquiryDeliveryChannel channel;
    private final LocalDateTime deliveredAt;
    private final UUID enquiryId;
    private final String recipientEmail;

    public AlreadyDeliveredException(
            EnquiryDeliveryChannel channel,
            LocalDateTime deliveredAt,
            UUID enquiryId,
            String recipientEmail) {
        super(
                ERROR_CODE,
                channel == EnquiryDeliveryChannel.EMAIL
                        ? "Owner contact details were already sent by email."
                        : "Owner contact details were already sent to ACOMI App.",
                HttpStatus.CONFLICT);
        this.channel = channel;
        this.deliveredAt = deliveredAt;
        this.enquiryId = enquiryId;
        this.recipientEmail = recipientEmail;
    }

    public EnquiryDeliveryChannel getChannel() {
        return channel;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public UUID getEnquiryId() {
        return enquiryId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }
}
