package com.acomi.acomi_backend.inquirycredit.api.dto.request;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdatePaymentConfigRequest {

    private String upiId;
    private UUID qrFileId;
    private String whatsappNumber;
    private String instructions;
    private Boolean enabled;
}
