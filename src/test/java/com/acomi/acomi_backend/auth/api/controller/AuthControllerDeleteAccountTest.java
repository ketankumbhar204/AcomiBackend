package com.acomi.acomi_backend.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.auth.application.service.AuthService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;

@ExtendWith(MockitoExtension.class)
class AuthControllerDeleteAccountTest {

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
    void deleteMe_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/auth/me"))
                .andExpect(status().isNoContent());

        verify(authService).deleteCurrentAccount();
    }

    @Test
    void deleteMe_doesNotAcceptUserIdFromClient() throws Exception {
        Method method = AuthController.class.getMethod("deleteCurrentAccount");

        assertThat(method.getParameterCount()).isZero();
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/me");
    }

    @Test
    void deleteAccountByOtp_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-deletion")
                        .contentType("application/json")
                        .content("{\"mobileNumber\":\"9876543210\",\"verificationToken\":\"delete-token\"}"))
                .andExpect(status().isNoContent());

        verify(authService).deleteAccountByOtp(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteAccountByPassword_returnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-deletion/password")
                        .contentType("application/json")
                        .content("{\"mobileNumber\":\"9876543210\",\"password\":\"Secret12\"}"))
                .andExpect(status().isNoContent());

        verify(authService).deleteAccountByPassword(org.mockito.ArgumentMatchers.any());
    }
}
