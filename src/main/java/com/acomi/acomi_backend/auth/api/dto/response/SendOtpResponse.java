package com.acomi.acomi_backend.auth.api.dto.response;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendOtpResponse {

    private String mobileNumber;
    private OtpPurpose purpose;
    private int expiresIn;
    private int resendAfter;
    private String message;
}
