package com.acomi.acomi_backend.property.api.dto.request;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin property lead. Fields are optional; service applies placeholders for incomplete leads. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Property registration submitted by an admin")
public class AdminCreatePropertyRegistrationRequest {

    private SpaceType propertyType;

    @Size(max = 150, message = "Property name must be at most 150 characters")
    private String propertyName;

    @Size(max = 120, message = "Owner name must be at most 120 characters")
    private String ownerName;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    private String description;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

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

    @DecimalMin(value = "0", message = "Starting price cannot be negative")
    @DecimalMax(value = "9999999999", message = "Starting price is too large")
    private BigDecimal startingPrice;

    @Min(value = 0, message = "Capacity cannot be negative")
    @Max(value = 100000, message = "Capacity is too large")
    private Integer capacityEstimate;

    @Valid
    private List<AmenityAssignmentDto> amenities = new ArrayList<>();

    @Schema(description = "When true, marks this admin lead as created for testing")
    private Boolean testLead;
}
