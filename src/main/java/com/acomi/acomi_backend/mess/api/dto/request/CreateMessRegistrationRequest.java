package com.acomi.acomi_backend.mess.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Public-website mess lead. Status, source and verification timestamps are server-derived.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mess registration submitted from the public website")
public class CreateMessRegistrationRequest {

    @NotBlank(message = "Mess name is required")
    @Size(max = 150, message = "Mess name must be at most 150 characters")
    @Schema(example = "Sunrise Mess")
    private String messName;

    @NotBlank(message = "Owner name is required")
    @Size(max = 120, message = "Owner name must be at most 120 characters")
    @Schema(example = "Ketan Kumbhar")
    private String ownerName;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    @Schema(description = "Optional short description of the mess")
    private String description;

    @NotBlank(message = "Mobile number is required")
    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Mobile number must be a valid 10-digit Indian number")
    @Schema(example = "9876543210")
    private String mobileNumber;

    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Alternate mobile number must be a valid 10-digit Indian number")
    @Schema(example = "9123456780", description = "Optional secondary owner contact number")
    private String alternateMobileNumber;

    public void setAlternateMobileNumber(String alternateMobileNumber) {
        this.alternateMobileNumber =
                alternateMobileNumber == null || alternateMobileNumber.isBlank()
                        ? null
                        : alternateMobileNumber.trim();
    }

    @NotBlank(message = "Verification token is required")
    @Schema(description = "Token returned by verify-otp for purpose MESS_REGISTRATION")
    private String verificationToken;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must be at most 255 characters")
    private String addressLine;

    @NotBlank(message = "City is required")
    @Size(max = 80, message = "City must be at most 80 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 80, message = "State must be at most 80 characters")
    private String state;

    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^[1-9]\\d{5}$", message = "Pincode must be a valid 6-digit Indian pincode")
    private String pincode;

    @Size(max = 512, message = "Map link must be at most 512 characters")
    @Schema(description = "Optional pasted Google Maps share link")
    private String mapUrl;

    @NotNull(message = "Monthly price is required")
    @DecimalMin(value = "0", message = "Monthly price cannot be negative")
    @DecimalMax(value = "9999999999", message = "Monthly price is too large")
    private BigDecimal monthlyPrice;

    @NotNull(message = "Per meal / tiffin price is required")
    @DecimalMin(value = "0", message = "Meal price cannot be negative")
    @DecimalMax(value = "9999999999", message = "Meal price is too large")
    private BigDecimal mealPrice;

    @Min(value = 0, message = "Capacity cannot be negative")
    @Max(value = 100000, message = "Capacity is too large")
    @Schema(description = "Optional approximate customers")
    private Integer capacityEstimate;
}
