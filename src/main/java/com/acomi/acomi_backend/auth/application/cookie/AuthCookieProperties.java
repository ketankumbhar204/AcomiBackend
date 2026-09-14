package com.acomi.acomi_backend.auth.application.cookie;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.auth.cookie")
public class AuthCookieProperties {

    /**
     * Cookie Domain attribute. Empty = host-only on the API host (api.acomi.in).
     * That is enough for www.acomi.in and app.acomi.in to send the cookie on
     * credentialed API calls without exposing it to every acomi.in subdomain.
     */
    private String domain = "";

    /** Must be true on HTTPS production. */
    private boolean secure = false;

    /** Lax is enough for same-site subdomains of acomi.in. */
    private String sameSite = "Lax";
}
