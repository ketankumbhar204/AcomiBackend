package com.acomi.acomi_backend.enquiry.api.dto.response;

import lombok.Builder;
import lombok.Value;

/**
 * Photo-request handoff. {@code fcmQueued} means a new in-app row was saved and
 * push will be attempted after commit. It is not a delivery receipt.
 * {@code internationalPhone} is only for the caller's WhatsApp composer.
 */
@Value
@Builder
public class PhotoRequestResponse {
    boolean notificationPersisted;
    boolean notificationDuplicate;
    boolean fcmQueued;
    boolean phoneAvailable;
    String internationalPhone;
}
