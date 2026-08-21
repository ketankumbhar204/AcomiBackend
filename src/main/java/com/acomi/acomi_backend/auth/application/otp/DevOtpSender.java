package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only OTP delivery. Logs the generated OTP so the real verification
 * flow can be tested without an SMS provider. Must never be enabled in production.
 */
public class DevOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(DevOtpSender.class);

    @Override
    public void send(String mobileNumber, String otp, OtpPurpose purpose) {
        log.info("[DEV OTP] purpose={} mobile={} otp={}", purpose, maskMobile(mobileNumber), otp);
    }

    static String maskMobile(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.length() < 4) {
            return "+91XXXXXXXXXX";
        }
        String last4 = mobileNumber.substring(mobileNumber.length() - 4);
        return "+91******" + last4;
    }
}
