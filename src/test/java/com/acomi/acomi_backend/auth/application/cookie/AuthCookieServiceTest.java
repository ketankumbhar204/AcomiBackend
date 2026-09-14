package com.acomi.acomi_backend.auth.application.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

    @Test
    void setAccessToken_writesHttpOnlyHostOnlyCookie() {
        AuthCookieProperties properties = new AuthCookieProperties();
        properties.setSecure(false);
        properties.setSameSite("Lax");
        properties.setDomain("");
        AuthCookieService service = new AuthCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setAccessToken(response, "jwt-token", 60_000);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("acomi_at=jwt-token");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Path=/");
        assertThat(header).contains("SameSite=Lax");
        assertThat(header).doesNotContain("Domain=");
        assertThat(header.toLowerCase()).doesNotContain("owner");
        assertThat(header.toLowerCase()).doesNotContain("mobile");
    }

    @Test
    void setAccessToken_appliesConfiguredParentDomain() {
        AuthCookieProperties properties = new AuthCookieProperties();
        properties.setSecure(true);
        properties.setDomain(".acomi.in");
        AuthCookieService service = new AuthCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.setAccessToken(response, "jwt-token", 60_000);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("Domain=.acomi.in");
        assertThat(header).contains("Secure");
    }

    @Test
    void sanitizeDomain_ignoresLocalhost() {
        assertThat(AuthCookieService.sanitizeDomain("localhost")).isNull();
        assertThat(AuthCookieService.sanitizeDomain("")).isNull();
        assertThat(AuthCookieService.sanitizeDomain(".acomi.in")).isEqualTo(".acomi.in");
    }

    @Test
    void clearAccessToken_expiresCookie() {
        AuthCookieProperties properties = new AuthCookieProperties();
        AuthCookieService service = new AuthCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clearAccessToken(response);

        String header = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).contains("acomi_at=");
        assertThat(header).contains("Max-Age=0");
    }
}
