package com.amico.amico_backend.accommodation.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Result of bulk bed creation")
public class BulkCreateBedsResponse {

    @Schema(description = "Beds created")
    private int bedsCreated;

    @Schema(description = "Created bed IDs")
    private List<UUID> bedIds;
}
