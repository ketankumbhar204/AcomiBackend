package com.acomi.acomi_backend.registration.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Partial update of owner contact on an admin property/mess lead. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Update owner contact numbers on a registration lead")
public class AdminUpdateRegistrationContactRequest {

    @Size(max = 120, message = "Owner name must be at most 120 characters")
    private String ownerName;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Alternate mobile number must be a valid 10-digit Indian number")
    private String alternateMobileNumber;

    public void setAlternateMobileNumber(String alternateMobileNumber) {
        this.alternateMobileNumber =
                alternateMobileNumber == null || alternateMobileNumber.isBlank()
                        ? null
                        : alternateMobileNumber.trim();
    }
}
