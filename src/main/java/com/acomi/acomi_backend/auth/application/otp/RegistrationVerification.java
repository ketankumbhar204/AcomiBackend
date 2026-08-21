package com.acomi.acomi_backend.auth.application.otp;

public record RegistrationVerification(String verificationToken, int expiresInSeconds) {}
