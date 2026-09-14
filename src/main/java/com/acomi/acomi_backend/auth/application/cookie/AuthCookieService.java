package com.acomi.acomi_backend.auth.application.cookie;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthCookieProperties properties;

    public void setAccessToken(HttpServletResponse response, String accessToken, long expiresInMs) {
        if (!StringUtils.hasText(accessToken) || response == null) {
            return;
        }
        long maxAgeSeconds = Math.max(1, expiresInMs / 1000);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(accessToken, maxAgeSeconds).toString());
    }

    public void clearAccessToken(HttpServletResponse response) {
        if (response == null) {
            return;
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0).toString());
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(AuthCookies.ACCESS_TOKEN, value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(normalizeSameSite(properties.getSameSite()))
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds));
        String domain = sanitizeDomain(properties.getDomain());
        if (domain != null) {
            builder.domain(domain);
        }
        return builder.build();
    }

    static String sanitizeDomain(String domain) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        String trimmed = domain.trim();
        if ("localhost".equalsIgnoreCase(trimmed) || "127.0.0.1".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String normalizeSameSite(String sameSite) {
        if (!StringUtils.hasText(sameSite)) {
            return "Lax";
        }
        String trimmed = sameSite.trim();
        if ("Strict".equalsIgnoreCase(trimmed) || "None".equalsIgnoreCase(trimmed) || "Lax".equalsIgnoreCase(trimmed)) {
            return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1).toLowerCase();
        }
        return "Lax";
    }
}
