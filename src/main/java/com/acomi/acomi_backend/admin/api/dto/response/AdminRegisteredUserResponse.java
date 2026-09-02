package com.acomi.acomi_backend.admin.api.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminRegisteredUserResponse {

    private UUID id;
    private String fullName;
    private String mobileNumber;
    private boolean mobileVerified;
    private LocalDateTime mobileVerifiedAt;
    private LocalDateTime registeredAt;
    /** OWNER, MEMBER, OWNER_AND_MEMBER, or NOT_SELECTED. Derived from space memberships. */
    private String selectedRole;
    /** COMPLETE when the user has at least one active space membership; otherwise INCOMPLETE. */
    private String onboardingStatus;
    private boolean profileCompleted;
    private List<AdminRegisteredUserSpaceResponse> spaces;
}
