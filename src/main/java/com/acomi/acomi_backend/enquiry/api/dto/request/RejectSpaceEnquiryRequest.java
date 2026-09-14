package com.acomi.acomi_backend.enquiry.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RejectSpaceEnquiryRequest {

    @Size(max = 500)
    private String reason;
}
