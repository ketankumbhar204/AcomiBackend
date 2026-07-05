package com.countin.countin_backend.user.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating the signed-in user's basic profile")
public class UpdateUserRequest {

    @NotBlank(message = "Full name is required")
    @Schema(description = "Display name", example = "John Doe")
    private String fullName;
}
