package com.acomi.acomi_backend.auth.api.dto.response;

import com.acomi.acomi_backend.user.api.dto.response.UserResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthTokenResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UserResponse user;
}
