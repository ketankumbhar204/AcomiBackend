package com.acomi.acomi_backend.listing.api.dto;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.property.domain.model.PriceBasis;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeListingResponse {

    public enum Kind {
        PROPERTY,
        MESS
    }

    /** LIVE is a converted space. PENDING is an unconverted lead linked to the caller. */
    public enum Publication {
        LIVE,
        PENDING
    }

    private UUID spaceId;
    private UUID registrationId;
    private Kind kind;
    private SpaceType spaceType;
    private String name;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String mapUrl;
    private String description;
    private String contactNumber;
    private GenderPolicy genderPolicy;
    private Boolean foodIncluded;
    private UUID photoFileId;
    private String coverImageUrl;
    private List<ListingPhotoResponse> gallery;
    private BigDecimal startingPrice;
    private BigDecimal monthlyPrice;
    private BigDecimal mealPrice;
    private PriceBasis priceBasis;
    private Publication publication;
    private String registrationReference;
    private String registrationStatus;
    private MembershipRole membershipRole;
    private LocalDateTime updatedAt;
    private boolean operationalManagementAvailable;
    private String publicPath;
    private String managePath;
    private String accommodationPath;
    private String mealsPath;

    @Getter
    @Builder
    public static class ListingPhotoResponse {
        private UUID id;
        private String contentUrl;
        private String contentType;
    }
}
