package com.acomi.acomi_backend.auth.application.otp;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.config.security.OtpProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OtpHashService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String secret;

    public OtpHashService(OtpProperties properties) {
        this.secret = properties.getHashSecret();
    }

    public String hashOtp(String mobileNumber, OtpPurpose purpose, String otp) {
        return hmac("otp:" + purpose.name() + ":" + mobileNumber + ":" + otp);
    }

    public String hashVerificationToken(String mobileNumber, OtpPurpose purpose, String token) {
        return hashVerificationToken(mobileNumber, purpose, token, null);
    }

    /**
     * CHANGE_MOBILE tokens include the authenticated user id so a token issued for one
     * account cannot be consumed by another, even for the same new mobile number.
     */
    public String hashVerificationToken(
            String mobileNumber, OtpPurpose purpose, String token, UUID actorUserId) {
        if (actorUserId != null) {
            return hmac("vtoken:" + purpose.name() + ":" + mobileNumber + ":" + actorUserId + ":" + token);
        }
        return hmac("vtoken:" + purpose.name() + ":" + mobileNumber + ":" + token);
    }

    public boolean matches(String expectedHash, String actualHash) {
        if (!StringUtils.hasText(expectedHash) || !StringUtils.hasText(actualHash)) {
            return false;
        }
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = actualHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String hmac(String material) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("acomi.otp.hash-secret is not configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Unable to hash OTP material", ex);
        }
    }
}
