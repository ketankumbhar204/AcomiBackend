package com.acomi.acomi_backend.member.api.dto.request;

import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.space.domain.model.MealBillingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for adding a member directly to a space")
public class CreateMemberRequest {

    @NotBlank(message = "Full name is required")
    @Schema(description = "Display name of the member", example = "Rahul Sharma")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Schema(description = "Contact mobile number", example = "9876543210")
    private String mobileNumber;

    @NotNull(message = "Role is required")
    @Schema(description = "Member role", example = "TENANT", implementation = MembershipRole.class)
    private MembershipRole role;

    @Schema(description = "Member gender for PG/hostel occupancy", example = "MALE", implementation = MemberGender.class)
    private MemberGender gender;

    @Schema(
            description = "Meal billing override for this member; null uses the space default",
            example = "PREPAID_BALANCE",
            implementation = MealBillingType.class)
    private MealBillingType mealBillingType;
}
