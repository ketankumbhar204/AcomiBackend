package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.config.security.OtpProperties;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OtpSenderConfiguration {

    @Bean
    public OtpSender otpSender(OtpProperties properties) {
        String sender = properties.getSender() == null
                ? "none"
                : properties.getSender().trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(sender) || "none".equals(sender)) {
            return new NoneOtpSender();
        }
        if ("dev".equals(sender)) {
            return new DevOtpSender();
        }
        throw new IllegalStateException(
                "Unsupported acomi.otp.sender '" + sender + "'. Supported values: none, dev.");
    }
}
