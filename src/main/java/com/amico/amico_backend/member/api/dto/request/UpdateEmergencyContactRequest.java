package com.amico.amico_backend.member.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for updating a member's emergency contact")
public class UpdateEmergencyContactRequest {

    @NotBlank(message = "Emergency contact name is required")
    @Schema(description = "Emergency contact full name", example = "Priya Sharma")
    private String emergencyContactName;

    @NotBlank(message = "Emergency contact relation is required")
    @Schema(description = "Relationship to the member", example = "Mother")
    private String emergencyContactRelation;

    @NotBlank(message = "Emergency contact mobile is required")
    @Schema(description = "Emergency contact mobile number", example = "9876543210")
    private String emergencyContactMobile;
}
