package com.acomi.acomi_backend.listing.api.dto;

import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Owner edit of an unconverted mess lead. Mobile and status stay unchanged. */
@Getter
@Setter
@NoArgsConstructor
public class UpdateOwnedMessLeadRequest {

    @Size(max = 150)
    private String messName;

    @Size(max = 150)
    private String buildingName;

    @Size(max = 120)
    private String ownerName;

    @Size(max = 2000)
    private String description;

    @Size(max = 500)
    private String addressLine;

    @Size(max = 80)
    private String city;

    @Size(max = 80)
    private String state;

    @Size(max = 12)
    private String pincode;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Size(max = 500)
    private String mapUrl;

    @DecimalMin(value = "0.0")
    private BigDecimal monthlyPrice;

    private PriceBasis monthlyPriceBasis;

    @DecimalMin(value = "0.0")
    private BigDecimal mealPrice;

    @Size(max = 500)
    private String sharingNotes;

    private Boolean foodIncludedListing;
    private GenderPolicy genderPolicy;

    /** When present, replaces the lead gallery. Null leaves existing photos. */
    private List<String> photos;
}
