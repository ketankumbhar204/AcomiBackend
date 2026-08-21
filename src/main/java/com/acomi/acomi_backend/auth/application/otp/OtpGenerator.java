package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.config.security.OtpProperties;
import java.security.SecureRandom;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OtpGenerator {

    private static final Set<String> DISALLOWED_SIX_DIGIT_CODES = Set.of("000000", "111111", "123456");

    private final SecureRandom secureRandom;
    private final OtpProperties properties;

    @Autowired
    public OtpGenerator(OtpProperties properties) {
        this(new SecureRandom(), properties);
    }

    OtpGenerator(SecureRandom secureRandom, OtpProperties properties) {
        this.secureRandom = secureRandom;
        this.properties = properties;
    }

    public String generate() {
        int length = properties.getLength();
        int bound = pow10(length);
        for (int attempt = 0; attempt < 32; attempt++) {
            String otp = String.format("%0" + length + "d", secureRandom.nextInt(bound));
            if (isAllowed(otp, length)) {
                return otp;
            }
        }
        throw new IllegalStateException("Unable to generate a valid OTP");
    }

    private static boolean isAllowed(String otp, int length) {
        if (otp.chars().allMatch(ch -> ch == '0')) {
            return false;
        }
        return length != 6 || !DISALLOWED_SIX_DIGIT_CODES.contains(otp);
    }

    private static int pow10(int length) {
        int value = 1;
        for (int i = 0; i < length; i++) {
            value *= 10;
        }
        return value;
    }
}
