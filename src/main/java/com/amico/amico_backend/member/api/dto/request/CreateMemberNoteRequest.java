package com.amico.amico_backend.member.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for adding a note to a member record")
public class CreateMemberNoteRequest {

    @NotBlank(message = "Note is required")
    @Schema(description = "Note content", example = "Member requested early checkout.")
    private String note;
}
