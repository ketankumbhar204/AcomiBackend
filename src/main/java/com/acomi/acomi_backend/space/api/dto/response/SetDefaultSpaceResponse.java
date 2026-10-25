package com.acomi.acomi_backend.space.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Confirmation after setting the default space")
public class SetDefaultSpaceResponse {

    private UUID spaceId;
    private String referenceCode;
    private String spaceName;
    @JsonProperty("isDefault")
    private boolean isDefault;
}
