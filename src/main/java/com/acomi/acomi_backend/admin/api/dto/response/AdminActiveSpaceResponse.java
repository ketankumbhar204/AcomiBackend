package com.acomi.acomi_backend.admin.api.dto.response;

import com.acomi.acomi_backend.space.domain.model.SpaceType;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminActiveSpaceResponse {

    private UUID id;
    private String name;
    private SpaceType type;
    private String address;
    private String contactNumber;
    private UUID ownerId;
    private String ownerName;
    private String ownerMobile;
    private LocalDateTime createdAt;
}
