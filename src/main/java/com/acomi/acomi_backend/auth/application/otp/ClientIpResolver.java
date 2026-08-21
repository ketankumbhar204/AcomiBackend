package com.acomi.acomi_backend.auth.application.otp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

public final class ClientIpResolver {

    private ClientIpResolver() {}

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (StringUtils.hasText(forwarded)) {
            return truncate(forwarded);
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (StringUtils.hasText(realIp)) {
            return truncate(realIp);
        }
        return truncate(request.getRemoteAddr());
    }

    private static String firstHeaderValue(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        int comma = header.indexOf(',');
        return comma < 0 ? header.trim() : header.substring(0, comma).trim();
    }

    private static String truncate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
