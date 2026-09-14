package com.acomi.acomi_backend.space.api.dto.response;

import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Public discovery card for an active space (no contact or owner fields)")
public class DiscoverSpaceCardResponse {

    private UUID spaceId;

    @Schema(example = "Sunrise PG")
    private String name;

    @Schema(description = "Space category", example = "PG", implementation = SpaceType.class)
    private SpaceType type;

    @Schema(description = "Full address when available")
    private String address;

    @Schema(description = "Amenity codes for filtering/icons")
    private List<String> amenityCodes;

    @Schema(description = "Amenity display labels for UI")
    private List<String> amenityLabels;

    @Schema(description = "When true, food is included in rent")
    private boolean foodIncludedInRent;

    @Schema(description = "Gender policy when set", implementation = GenderPolicy.class)
    private GenderPolicy genderPolicy;

    @Schema(description = "True if the caller has an ACTIVE membership in this space")
    private boolean alreadyMember;

    @Schema(description = "True when this Space was converted from a test-lead registration")
    private boolean testSpace;
}
