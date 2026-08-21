package com.acomi.acomi_backend.auth.api.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerifyOtpResponse {

    private boolean verified;
    private String verificationToken;
    private int expiresIn;
}
