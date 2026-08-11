package com.amico.amico_backend.space.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Confirmation after setting the default space")
public class SetDefaultSpaceResponse {

    private UUID spaceId;
    private String spaceName;
    private boolean isDefault;
}
