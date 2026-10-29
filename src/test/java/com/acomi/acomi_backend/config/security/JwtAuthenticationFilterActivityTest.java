package com.acomi.acomi_backend.config.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.user.application.service.UserActivityService;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterActivityTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private UserActivityService userActivityService;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, userActivityService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedRequest_recordsLastUseAndNotASignIn() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = UserEntity.builder()
                .mobileNumber("9876543210")
                .fullName("Member")
                .isActive(true)
                .build();
        user.setId(userId);
        when(jwtService.isTokenValid("secret-token")).thenReturn(true);
        when(jwtService.extractUserId("secret-token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(new UserPrincipal(user));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        verify(userActivityService).touchActivity(userId);
        verify(userActivityService, never()).recordSignIn(any());
    }

    @Test
    void missingAccount_doesNotRecordActivity() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.isTokenValid("secret-token")).thenReturn(true);
        when(jwtService.extractUserId("secret-token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenThrow(new UsernameNotFoundException("gone"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer secret-token");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        verify(userActivityService, never()).touchActivity(any());
    }

    @Test
    void invalidToken_doesNotRecordActivity() throws Exception {
        when(jwtService.isTokenValid("nope")).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer nope");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        verifyNoInteractions(userActivityService);
    }
}
