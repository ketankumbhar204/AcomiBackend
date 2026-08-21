package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;

public interface OtpRateLimiter {

    void assertSendAllowed(String mobileNumber, OtpPurpose purpose, String requestIp);
}
