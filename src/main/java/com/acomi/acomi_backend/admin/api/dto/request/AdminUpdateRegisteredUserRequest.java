package com.acomi.acomi_backend.admin.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin update of a registered USER account profile. Does not accept systemRole —
 * platform role remains unchanged.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminUpdateRegisteredUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    /** Optional. When set, must be 8–72 chars and match confirmPassword (enforced in service). */
    private String password;

    private String confirmPassword;
}
