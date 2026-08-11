package com.amico.amico_backend.user.infrastructure.persistence.entity;

import com.amico.amico_backend.common.model.BaseEntity;
import com.amico.amico_backend.member.domain.model.MemberGender;
import com.amico.amico_backend.user.domain.model.KycStatus;
import com.amico.amico_backend.user.domain.model.ProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @Column(name = "mobile_number", nullable = false, unique = true, length = 15)
    private String mobileNumber;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 32)
    private MemberGender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "permanent_address")
    private String permanentAddress;

    @Column(name = "city", length = 128)
    private String city;

    @Column(name = "state", length = 128)
    private String state;

    @Column(name = "pincode", length = 16)
    private String pincode;

    @Builder.Default
    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted = false;

    @Column(name = "profile_completed_at")
    private LocalDateTime profileCompletedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false, length = 32)
    private ProfileStatus profileStatus = ProfileStatus.PENDING;

    @Builder.Default
    @Column(name = "profile_completion_percentage", nullable = false)
    private int profileCompletionPercentage = 0;

    @Builder.Default
    @Column(name = "documents_uploaded", nullable = false)
    private int documentsUploaded = 0;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 32)
    private KycStatus kycStatus = KycStatus.NOT_STARTED;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
