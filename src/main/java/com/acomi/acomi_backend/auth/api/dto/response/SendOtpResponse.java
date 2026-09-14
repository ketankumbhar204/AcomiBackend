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
    /** True only when local registration OTP skip issued a verification token. */
    private boolean otpSkipped;
    /**
     * Present only when {@link #otpSkipped} is true. Clients must treat absence as requiring
     * the normal verify-otp step.
     */
    private String verificationToken;
}
