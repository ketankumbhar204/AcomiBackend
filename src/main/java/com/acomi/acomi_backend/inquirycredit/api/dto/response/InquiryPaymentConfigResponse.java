package com.acomi.acomi_backend.inquirycredit.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InquiryPaymentConfigResponse {

    private UUID configId;
    private boolean enabled;
    private String upiId;
    private UUID qrFileId;
    private String qrUrl;
    private String whatsappNumber;
    private String instructions;
    private List<InquiryCreditPackageResponse> packages;
}
