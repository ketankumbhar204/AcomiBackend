package com.acomi.acomi_backend.mess.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin mess lead. Fields are optional; service applies placeholders for incomplete leads. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Mess registration submitted by an admin")
public class AdminCreateMessRegistrationRequest {

    @Size(max = 150, message = "Mess name must be at most 150 characters")
    private String messName;

    @Size(max = 120, message = "Owner name must be at most 120 characters")
    private String ownerName;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Alternate mobile number must be a valid 10-digit Indian number")
    @Schema(description = "Optional secondary owner contact number")
    private String alternateMobileNumber;

    public void setAlternateMobileNumber(String alternateMobileNumber) {
        this.alternateMobileNumber =
                alternateMobileNumber == null || alternateMobileNumber.isBlank()
                        ? null
                        : alternateMobileNumber.trim();
    }

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String addressLine;

    @Size(max = 80, message = "City must be at most 80 characters")
    private String city;

    @Size(max = 80, message = "State must be at most 80 characters")
    private String state;

    @Pattern(regexp = "^[1-9]\\d{5}$", message = "Pincode must be a valid 6-digit Indian pincode")
    private String pincode;

    @Size(max = 512, message = "Map link must be at most 512 characters")
    private String mapUrl;

    @DecimalMin(value = "0", message = "Monthly price cannot be negative")
    @DecimalMax(value = "9999999999", message = "Monthly price is too large")
    private BigDecimal monthlyPrice;

    @DecimalMin(value = "0", message = "Meal price cannot be negative")
    @DecimalMax(value = "9999999999", message = "Meal price is too large")
    private BigDecimal mealPrice;

    @Min(value = 0, message = "Capacity cannot be negative")
    @Max(value = 100000, message = "Capacity is too large")
    private Integer capacityEstimate;

    @Schema(description = "When true, marks this admin lead as created for testing")
    private Boolean testLead;
}
