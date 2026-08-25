package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.config.security.OtpProperties;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class OtpSenderConfiguration {

    @Bean
    public OtpSender otpSender(OtpProperties properties) {
        String sender = normalizedSender(properties);
        if (!StringUtils.hasText(sender) || "none".equals(sender)) {
            return new NoneOtpSender();
        }
        if ("dev".equals(sender)) {
            return new DevOtpSender();
        }
        if ("twofactor".equals(sender)) {
            return new ExternalManagedOtpSender();
        }
        throw new IllegalStateException(
                "Unsupported acomi.otp.sender '" + sender + "'. Supported values: none, dev, twofactor.");
    }

    @Bean
    @ConditionalOnProperty(name = "acomi.otp.sender", havingValue = "twofactor")
    public TwoFactorOtpClient twoFactorOtpClient(OtpProperties properties) {
        return new TwoFactorOtpClient(properties, TwoFactorOtpClient.createRestClient(properties.getTwoFactor()));
    }

    static String normalizedSender(OtpProperties properties) {
        return properties.getSender() == null
                ? "none"
                : properties.getSender().trim().toLowerCase(Locale.ROOT);
    }
}
