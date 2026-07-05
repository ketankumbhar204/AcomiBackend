package com.countin.countin_backend.user.api.dto.response;

import com.countin.countin_backend.member.domain.model.MemberGender;
import com.countin.countin_backend.user.domain.model.KycStatus;
import com.countin.countin_backend.user.domain.model.ProfileStatus;
import com.countin.countin_backend.user.infrastructure.persistence.entity.UserEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private UUID id;
    private String mobileNumber;
    private String fullName;
    private String profilePhotoUrl;
    private boolean active;
    private LocalDateTime createdAt;
    private String email;
    private MemberGender gender;
    private LocalDate dateOfBirth;
    private String permanentAddress;
    private String city;
    private String state;
    private String pincode;
    private Boolean profileCompleted;
    private LocalDateTime profileCompletedAt;
    private ProfileStatus profileStatus;
    private Integer profileCompletionPercentage;
    private Integer documentsUploaded;
    private KycStatus kycStatus;

    public static UserResponse from(UserEntity user) {
        return UserResponse.builder()
                .id(user.getId())
                .mobileNumber(user.getMobileNumber())
                .fullName(user.getFullName())
                .profilePhotoUrl(user.getProfilePhotoUrl())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .email(user.getEmail())
                .gender(user.getGender())
                .dateOfBirth(user.getDateOfBirth())
                .permanentAddress(user.getPermanentAddress())
                .city(user.getCity())
                .state(user.getState())
                .pincode(user.getPincode())
                .profileCompleted(user.isProfileCompleted())
                .profileCompletedAt(user.getProfileCompletedAt())
                .profileStatus(user.getProfileStatus())
                .profileCompletionPercentage(user.getProfileCompletionPercentage())
                .documentsUploaded(user.getDocumentsUploaded())
                .kycStatus(user.getKycStatus())
                .build();
    }
}
