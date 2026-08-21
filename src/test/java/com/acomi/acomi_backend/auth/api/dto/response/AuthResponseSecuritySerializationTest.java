package com.acomi.acomi_backend.auth.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.acomi.acomi_backend.user.api.dto.response.UserResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthResponseSecuritySerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendOtpResponse_doesNotIncludeOtpOrHash() throws Exception {
        SendOtpResponse response = SendOtpResponse.builder()
                .mobileNumber("9876543210")
                .purpose(OtpPurpose.REGISTER)
                .expiresIn(300)
                .resendAfter(60)
                .message("OTP sent successfully")
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain("\"otp\"");
        assertThat(json).doesNotContain("codeHash");
        assertThat(json).doesNotContain("verificationToken");
        assertThat(json).contains("\"expiresIn\":300");
        assertThat(json).contains("REGISTER");
    }

    @Test
    void verifyOtpResponse_includesTokenButNotOtpOrJwt() throws Exception {
        VerifyOtpResponse response = VerifyOtpResponse.builder()
                .verified(true)
                .verificationToken("registration-token")
                .expiresIn(600)
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"verified\":true");
        assertThat(json).contains("registration-token");
        assertThat(json).doesNotContain("\"otp\"");
        assertThat(json).doesNotContain("accessToken");
        assertThat(json).doesNotContain("codeHash");
    }

    @Test
    void authTokenResponse_doesNotIncludePassword() throws Exception {
        AuthTokenResponse response = AuthTokenResponse.builder()
                .accessToken("jwt-token")
                .tokenType("Bearer")
                .expiresIn(86_400_000L)
                .user(UserResponse.builder()
                        .id(UUID.randomUUID())
                        .mobileNumber("9876543210")
                        .fullName("Priya Sharma")
                        .active(true)
                        .build())
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("Secret12");
        assertThat(json).doesNotContain("passwordHash");
        assertThat(json).contains("jwt-token");
    }
}
