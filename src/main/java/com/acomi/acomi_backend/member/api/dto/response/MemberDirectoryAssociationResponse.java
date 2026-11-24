package com.acomi.acomi_backend.member.api.dto.response;

import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "One space-specific association for a directory person")
public class MemberDirectoryAssociationResponse {

    private UUID memberId;
    private UUID spaceId;
    private String spaceName;
    private SpaceType spaceType;
    private MembershipRole role;
    private MemberStatus membershipStatus;
    private MemberOccupancyStatus occupancyStatus;

    public static MemberDirectoryAssociationResponse from(MemberEntity member) {
        SpaceEntity space = member.getSpace();
        return MemberDirectoryAssociationResponse.builder()
                .memberId(member.getId())
                .spaceId(space.getId())
                .spaceName(space.getName())
                .spaceType(space.getType())
                .role(member.getRole())
                .membershipStatus(member.getStatus())
                .occupancyStatus(member.getOccupancyStatus())
                .build();
    }
}
