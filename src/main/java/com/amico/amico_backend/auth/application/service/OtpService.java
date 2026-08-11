package com.amico.amico_backend.auth.application.service;

import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.config.security.OtpProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpProperties otpProperties;

    public void sendOtp(String mobileNumber) {
        log.info("MVP OTP sent to {}. Use OTP: {}", mobileNumber, otpProperties.getMvpCode());
    }

    public void verifyOtp(String mobileNumber, String otp) {
        if (!otpProperties.getMvpCode().equals(otp)) {
            throw new BusinessException("Invalid OTP");
        }
        log.info("OTP verified for {}", mobileNumber);
    }
}
