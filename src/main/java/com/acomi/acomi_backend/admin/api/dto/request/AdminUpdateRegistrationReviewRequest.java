package com.acomi.acomi_backend.admin.api.dto.request;

import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Partial admin review/enrichment update for a registration lead before convert. */
@Getter
@Setter
@NoArgsConstructor
public class AdminUpdateRegistrationReviewRequest {

    @Size(max = 150)
    private String propertyOrMessName;

    @Size(max = 120)
    private String ownerName;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Alternate mobile number must be a valid 10-digit Indian number")
    private String alternateMobileNumber;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Additional mobile number must be a valid 10-digit Indian number")
    private String additionalMobileNumber;

    @Size(max = 255)
    private String addressLine;

    @Size(max = 80)
    private String city;

    @Size(max = 80)
    private String state;

    @Pattern(regexp = "^[1-9]\\d{5}$", message = "Pincode must be a valid 6-digit Indian pincode")
    private String pincode;

    @Size(max = 512)
    private String mapUrl;

    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private BigDecimal latitude;

    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private BigDecimal longitude;

    private BigDecimal startingOrMonthlyPrice;

    private BigDecimal mealPrice;

    private GenderPolicy genderPolicy;

    private Boolean foodIncludedListing;

    @Size(max = 4000)
    private String sharingNotes;

    @Size(max = 4000)
    private String unmappedAmenities;

    @Size(max = 4000)
    private String reviewNotes;

    @Valid
    private List<AmenityAssignmentDto> amenities;
}
