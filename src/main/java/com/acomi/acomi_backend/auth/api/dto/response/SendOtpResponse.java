package com.acomi.acomi_backend.auth.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SendOtpResponse {

    private String mobileNumber;
    private String message;
}
