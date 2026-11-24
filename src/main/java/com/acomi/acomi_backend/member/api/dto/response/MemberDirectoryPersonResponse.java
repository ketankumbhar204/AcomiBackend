package com.acomi.acomi_backend.member.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Unique person in the owner-level member directory")
public class MemberDirectoryPersonResponse {

    @Schema(description = "Stable directory identity key (u:{userId} or m:{mobile})")
    private String directoryId;

    @Schema(description = "Linked user account when known")
    private UUID userId;

    private String displayName;
    private String mobileNumber;

    @Schema(description = "Profile photo URL from the linked user when available")
    private String profilePhotoUrl;

    private List<MemberDirectoryAssociationResponse> associations;
}
