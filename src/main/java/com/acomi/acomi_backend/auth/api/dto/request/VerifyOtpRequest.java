package com.acomi.acomi_backend.auth.api.dto.request;

import com.acomi.acomi_backend.auth.domain.model.OtpPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VerifyOtpRequest {

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    private String otp;

    /**
     * Required for {@code POST /auth/verify-otp}. Ignored by account-deletion,
     * which always verifies {@code ACCOUNT_DELETION}.
     */
    private OtpPurpose purpose;
}
