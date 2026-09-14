package com.acomi.acomi_backend.auth.application.cookie;

/**
 * HttpOnly session cookie used so www.acomi.in and app.acomi.in can share the same JWT
 * without putting the access token in a URL. JavaScript cannot read this cookie.
 */
public final class AuthCookies {

    public static final String ACCESS_TOKEN = "acomi_at";

    private AuthCookies() {}
}
