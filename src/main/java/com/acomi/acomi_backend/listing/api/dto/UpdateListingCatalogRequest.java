package com.acomi.acomi_backend.listing.api.dto;

import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Listing-level fields for a live space. Space columns and the linked registration are updated
 * together. Listing price is not copied onto beds, rooms, or payments.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateListingCatalogRequest {

    @Size(max = 150)
    private String name;

    @Size(max = 150)
    private String buildingName;

    @Size(max = 120)
    private String ownerName;

    @Size(max = 500)
    private String address;

    @Size(max = 80)
    private String city;

    @Size(max = 80)
    private String state;

    @Size(max = 12)
    private String pincode;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal latitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal longitude;

    @Size(max = 20)
    private String contactNumber;

    @Size(max = 500)
    private String mapUrl;

    @Size(max = 2000)
    private String description;

    private GenderPolicy genderPolicy;

    @Size(max = 500)
    private String sharingNotes;

    @Valid
    private List<AmenityAssignmentDto> amenities;

    private Boolean foodIncludedInRent;

    @DecimalMin("0.0")
    private BigDecimal startingPrice;

    private PriceBasis priceBasis;

    @DecimalMin("0.0")
    private BigDecimal monthlyPrice;

    private PriceBasis monthlyPriceBasis;

    @DecimalMin("0.0")
    private BigDecimal mealPrice;

    @Min(0)
    private Integer capacityEstimate;

    /** When present, replaces the linked registration gallery. */
    private List<String> photos;
}
