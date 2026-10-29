package com.acomi.acomi_backend.admin.api.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminRegisteredUserResponse {

    private UUID id;
    private String referenceCode;
    private String fullName;
    private String mobileNumber;
    private String email;
    private boolean mobileVerified;
    private LocalDateTime mobileVerifiedAt;
    private LocalDateTime registeredAt;
    private LocalDateTime updatedAt;
    /** OWNER, MEMBER, OWNER_AND_MEMBER, or NOT_SELECTED. Derived from space memberships. */
    private String selectedRole;
    /** COMPLETE when the user has at least one active space membership; otherwise INCOMPLETE. */
    private String onboardingStatus;
    private boolean profileCompleted;
    private List<AdminRegisteredUserSpaceResponse> spaces;
    /** Platform system role: USER or ADMIN. */
    private String systemRole;
    private boolean active;
    /** Admin-marked test account (excluded from production analytics when filtered). */
    private boolean testUser;
    /** Last successful sign-in, with the ACOMI timezone offset. Null when none was recorded. */
    private OffsetDateTime lastLoginAt;
    /** Last authenticated use, with the ACOMI timezone offset. Null when none was recorded. */
    private OffsetDateTime lastActiveAt;
    /**
     * False when this account predates activity tracking and has not been observed since. A null
     * timestamp then means unknown, not "never".
     */
    private boolean activityTracked;
}
