package com.acomi.acomi_backend.auth.application.otp;

public record OtpDispatchResult(int expiresInSeconds, int resendAfterSeconds) {}
