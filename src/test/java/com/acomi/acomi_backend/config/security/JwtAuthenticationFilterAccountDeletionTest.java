package com.acomi.acomi_backend.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterAccountDeletionTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deletedAccountTokenLeavesRequestUnauthenticated() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer leftover-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.isTokenValid("leftover-token")).thenReturn(true);
        when(jwtService.extractUserId("leftover-token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId))
                .thenThrow(new UsernameNotFoundException("User not found"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void httpOnlyCookieTokenAuthenticatesWhenBearerHeaderIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("acomi_at", "cookie-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.springframework.security.core.userdetails.UserDetails user =
                org.springframework.security.core.userdetails.User.withUsername(userId.toString())
                        .password("n")
                        .authorities("ROLE_USER")
                        .build();

        when(jwtService.isTokenValid("cookie-token")).thenReturn(true);
        when(jwtService.extractUserId("cookie-token")).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(user);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void missingTokenLeavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
