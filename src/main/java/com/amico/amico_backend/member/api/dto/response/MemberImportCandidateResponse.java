package com.amico.amico_backend.member.api.dto.response;

import com.amico.amico_backend.member.domain.model.MemberGender;
import com.amico.amico_backend.member.domain.model.MemberStatus;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Eligible resident that can be reused for Move In / Reserve in the target space")
public class MemberImportCandidateResponse {

    @Schema(description = "Member id in the source space (or target space when alreadyInTargetSpace)")
    private UUID memberId;

    private String fullName;
    private String mobileNumber;
    private MembershipRole role;
    private MemberStatus status;
    private MemberOccupancyStatus occupancyStatus;
    private MemberGender gender;
    private LocalDateTime createdAt;

    private UUID sourceSpaceId;
    private String sourceSpaceName;

    @Schema(description = "True when this member already exists in the target space")
    private boolean alreadyInTargetSpace;

    @Schema(description = "True when the resident has no ACTIVE/RESERVED occupancy across managed lodging spaces")
    private boolean availableForMoveIn;

    public static MemberImportCandidateResponse from(
            MemberEntity member, UUID targetSpaceId, boolean availableForMoveIn) {
        SpaceEntity space = member.getSpace();
        boolean alreadyInTarget = space.getId().equals(targetSpaceId);
        return MemberImportCandidateResponse.builder()
                .memberId(member.getId())
                .fullName(member.getFullName())
                .mobileNumber(member.getMobileNumber())
                .role(member.getRole())
                .status(member.getStatus())
                .occupancyStatus(member.getOccupancyStatus())
                .gender(member.getGender())
                .createdAt(member.getCreatedAt())
                .sourceSpaceId(space.getId())
                .sourceSpaceName(space.getName())
                .alreadyInTargetSpace(alreadyInTarget)
                .availableForMoveIn(availableForMoveIn)
                .build();
    }
}
