package com.acomi.acomi_backend.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A 429 that also tells the caller how long to wait. Clients render a countdown
 * from {@code retryAfterSeconds} instead of a dead-end "try again later" message.
 */
public class RateLimitedException extends BusinessException {

    public static final String ERROR_CODE = "RATE_LIMITED";

    private final long retryAfterSeconds;

    public RateLimitedException(String message, long retryAfterSeconds) {
        super(ERROR_CODE, message, HttpStatus.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
