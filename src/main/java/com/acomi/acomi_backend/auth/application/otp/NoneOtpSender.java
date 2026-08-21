package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Production-safe default until a real SMS provider is wired. Does not log OTP codes.
 */
public class NoneOtpSender implements OtpSender {

    private static final Logger log = LoggerFactory.getLogger(NoneOtpSender.class);

    @Override
    public void send(String mobileNumber, String otp, OtpPurpose purpose) {
        log.warn("OTP delivery is unavailable");
        throw new BusinessException(
                "Unable to send OTP. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
