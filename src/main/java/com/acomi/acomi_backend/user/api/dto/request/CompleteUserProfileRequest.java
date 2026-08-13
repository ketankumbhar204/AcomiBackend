package com.acomi.acomi_backend.user.api.dto.request;

import com.acomi.acomi_backend.member.domain.model.MemberDocumentType;
import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.user.domain.model.ProfileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "Request body for completing the signed-in user's onboarding profile")
public class CompleteUserProfileRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private MemberGender gender;

    private String dateOfBirth;

    private String email;

    private String profilePhotoUrl;

    @NotBlank(message = "Permanent address is required")
    private String permanentAddress;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Pincode is required")
    private String pincode;

    private String emergencyContactName;

    private String emergencyContactMobile;

    private String emergencyContactRelation;

    private MemberDocumentType identityDocumentType;

    private String identityDocumentNumber;

    private String addressProofFileUrl;

    private String identityProofFileUrl;

    private String additionalDocumentFileUrl;

    private Boolean profileCompleted;

    private ProfileStatus profileStatus;
}
