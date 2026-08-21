package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;

/**
 * Delivery mechanism for a generated OTP. Authentication logic must not depend
 * on whether the implementation logs locally or sends SMS.
 */
public interface OtpSender {

    void send(String mobileNumber, String otp, OtpPurpose purpose);
}
