package com.acomi.acomi_backend.payment.infrastructure.delivery;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Vendor-agnostic template send request for WhatsApp Cloud API style providers. */
@Getter
@Builder
public class WhatsAppTemplateSendRequest {

    private String toDigits;
    private String templateName;
    private String languageCode;
    private List<String> bodyParameters;
}
