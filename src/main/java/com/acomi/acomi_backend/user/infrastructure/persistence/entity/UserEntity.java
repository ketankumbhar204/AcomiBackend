package com.acomi.acomi_backend.user.infrastructure.persistence.entity;

import com.acomi.acomi_backend.common.model.BaseEntity;
import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.user.domain.model.KycStatus;
import com.acomi.acomi_backend.user.domain.model.ProfileStatus;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @Column(name = "mobile_number", nullable = false, length = 64)
    private String mobileNumber;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "profile_photo_file_id")
    private UUID profilePhotoFileId;

    @Column(name = "email")
    private String email;

    /** Emails previously used on space enquiries, newest first. Not the profile email column. */
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "enquiry_emails", nullable = false, columnDefinition = "jsonb")
    private List<String> enquiryEmails = new ArrayList<>();

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

    @Builder.Default
    @Column(name = "test_user", nullable = false)
    private boolean testUser = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @JsonIgnore
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "mobile_verified_at")
    private LocalDateTime mobileVerifiedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", nullable = false, length = 20)
    private SystemRole systemRole = SystemRole.USER;

    @JsonIgnore
    public boolean isLinkableOwner() {
        return isActive && systemRole == SystemRole.USER;
    }

    @JsonIgnore
    public boolean isPlatformAdmin() {
        return systemRole == SystemRole.ADMIN;
    }

    public static final int MAX_ENQUIRY_EMAILS = 10;

    /**
     * Remembers an enquiry email. Newest address stays first in {@link #enquiryEmails}.
     * Fills {@link #email} only when the profile field is still blank.
     */
    public void rememberEnquiryEmail(String raw) {
        String normalized = normalizeEnquiryEmail(raw);
        if (normalized == null) {
            return;
        }
        List<String> emails = new ArrayList<>();
        if (enquiryEmails != null) {
            for (String existing : enquiryEmails) {
                String value = normalizeEnquiryEmail(existing);
                if (value != null && !value.equals(normalized)) {
                    emails.add(value);
                }
            }
        }
        emails.add(0, normalized);
        if (emails.size() > MAX_ENQUIRY_EMAILS) {
            emails = new ArrayList<>(emails.subList(0, MAX_ENQUIRY_EMAILS));
        }
        this.enquiryEmails = emails;
        if (normalizeEnquiryEmail(email) == null) {
            this.email = normalized;
        }
    }

    /** Saved enquiry emails plus profile email when it is not already in the list. */
    @JsonIgnore
    public List<String> enquiryEmailOptions() {
        LinkedHashSet<String> options = new LinkedHashSet<>();
        if (enquiryEmails != null) {
            for (String existing : enquiryEmails) {
                String value = normalizeEnquiryEmail(existing);
                if (value != null) {
                    options.add(value);
                }
            }
        }
        String profile = normalizeEnquiryEmail(email);
        if (profile != null) {
            options.add(profile);
        }
        List<String> list = new ArrayList<>(options);
        if (list.size() > MAX_ENQUIRY_EMAILS) {
            return List.copyOf(list.subList(0, MAX_ENQUIRY_EMAILS));
        }
        return List.copyOf(list);
    }

    private static String normalizeEnquiryEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
