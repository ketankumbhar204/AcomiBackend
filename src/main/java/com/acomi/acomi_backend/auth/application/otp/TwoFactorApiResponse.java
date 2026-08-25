package com.acomi.acomi_backend.auth.application.otp;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TwoFactorApiResponse(
        @JsonProperty("Status") @JsonAlias({"status", "STATUS"}) String status,
        @JsonProperty("Details") @JsonAlias({"details", "DETAILS"}) String details) {

    boolean isSuccess() {
        return status != null && "success".equalsIgnoreCase(status.trim());
    }

    String detailsLower() {
        return StringUtils.hasText(details) ? details.trim().toLowerCase() : "";
    }
}
