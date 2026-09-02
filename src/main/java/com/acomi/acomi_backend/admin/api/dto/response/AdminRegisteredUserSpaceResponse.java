package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminRegisteredUserSpaceResponse {

    private UUID id;
    private String name;
    private SpaceType type;
    private MembershipRole membershipRole;
}
