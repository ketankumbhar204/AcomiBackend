package com.acomi.acomi_backend.auth.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.auth.api.dto.request.LoginRequest;
import com.acomi.acomi_backend.auth.api.dto.request.RegisterRequest;
import com.acomi.acomi_backend.auth.api.dto.response.AuthTokenResponse;
import com.acomi.acomi_backend.auth.application.service.AuthService;
import com.acomi.acomi_backend.user.api.dto.response.UserResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerPasswordAuthTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void register_returnsOk() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(tokenResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Priya Sharma",
                                  "mobileNumber": "9876543210",
                                  "password": "Secret12",
                                  "confirmPassword": "Secret12"
                                }
                                """))
                .andExpect(status().isOk());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void login_returnsOk() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokenResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "mobileNumber": "9876543210",
                                  "password": "Secret12"
                                }
                                """))
                .andExpect(status().isOk());

        verify(authService).login(any(LoginRequest.class));
    }

    private static AuthTokenResponse tokenResponse() {
        return AuthTokenResponse.builder()
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
    }
}
