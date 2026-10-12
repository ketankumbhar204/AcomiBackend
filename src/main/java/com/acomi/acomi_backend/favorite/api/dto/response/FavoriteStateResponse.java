package com.acomi.acomi_backend.favorite.api.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FavoriteStateResponse {

    private UUID spaceId;
    private boolean favorited;
}
