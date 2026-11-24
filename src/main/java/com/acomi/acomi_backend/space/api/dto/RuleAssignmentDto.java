package com.acomi.acomi_backend.space.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Space-specific house rule with stable code and display text")
public class RuleAssignmentDto {

    @Schema(description = "Stable rule code such as NO_SMOKING or CUSTOM", example = "NO_SMOKING")
    private String code;

    @Schema(
            description = "Rule text saved for this space (may differ from the default suggestion)",
            example = "No smoking on the premises")
    private String label;
}
