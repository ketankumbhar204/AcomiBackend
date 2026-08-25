package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;

/**
 * Placeholder sender when OTP delivery is owned by an external provider such as 2Factor.
 * {@link OtpService} must not generate or pass a local OTP through this class.
 */
final class ExternalManagedOtpSender implements OtpSender {

    @Override
    public void send(String mobileNumber, String otp, OtpPurpose purpose) {
        throw new IllegalStateException("OTP delivery is handled by the external provider");
    }
}
