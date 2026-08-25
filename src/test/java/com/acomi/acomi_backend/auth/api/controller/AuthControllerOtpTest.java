package com.acomi.acomi_backend.auth.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.auth.api.dto.request.SendOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.request.VerifyOtpRequest;
import com.acomi.acomi_backend.auth.api.dto.response.SendOtpResponse;
import com.acomi.acomi_backend.auth.api.dto.response.VerifyOtpResponse;
import com.acomi.acomi_backend.auth.application.service.AuthService;
import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerOtpTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void sendOtp_doesNotReturnOtp() throws Exception {
        when(authService.sendOtp(any(SendOtpRequest.class), any()))
                .thenReturn(SendOtpResponse.builder()
                        .mobileNumber("9876543210")
                        .purpose(OtpPurpose.REGISTER)
                        .expiresIn(300)
                        .resendAfter(60)
                        .message("OTP sent successfully")
                        .build());

        mockMvc.perform(post("/api/v1/auth/send-otp")
                        .contentType("application/json")
                        .content("""
                                {"mobileNumber":"9876543210","purpose":"REGISTER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobileNumber").value("9876543210"))
                .andExpect(jsonPath("$.data.purpose").value("REGISTER"))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.otp").doesNotExist());

        verify(authService).sendOtp(any(SendOtpRequest.class), any());
    }

    @Test
    void sendOtp_loginPurpose_doesNotReturnOtp() throws Exception {
        when(authService.sendOtp(any(SendOtpRequest.class), any()))
                .thenReturn(SendOtpResponse.builder()
                        .mobileNumber("9876543210")
                        .purpose(OtpPurpose.LOGIN)
                        .expiresIn(300)
                        .resendAfter(60)
                        .message("OTP sent successfully")
                        .build());

        mockMvc.perform(post("/api/v1/auth/send-otp")
                        .contentType("application/json")
                        .content("""
                                {"mobileNumber":"9876543210","purpose":"LOGIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("LOGIN"))
                .andExpect(jsonPath("$.data.otp").doesNotExist());
    }

    @Test
    void verifyOtp_returnsVerificationTokenWithoutJwt() throws Exception {
        when(authService.verifyOtp(any(VerifyOtpRequest.class)))
                .thenReturn(VerifyOtpResponse.builder()
                        .verified(true)
                        .verificationToken("registration-token")
                        .expiresIn(600)
                        .build());

        mockMvc.perform(post("/api/v1/auth/verify-otp")
                        .contentType("application/json")
                        .content("""
                                {"mobileNumber":"9876543210","otp":"482731","purpose":"REGISTER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verificationToken").value("registration-token"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.user").doesNotExist());
    }
}
