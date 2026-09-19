package com.acomi.acomi_backend.admin.api.dto.request;

import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-only create of a QA/test registered user. Does not accept systemRole —
 * the service always forces {@code USER} and {@code testUser=true}.
 *
 * <p>{@code spaceRole} is a {@link MembershipRole} (space membership), never a platform role.
 * OWNER creates a new space for the user; other roles may optionally join an existing
 * {@code spaceId}, or omit it to create a user with no space membership.
 */
@Getter
@Setter
@NoArgsConstructor
public class AdminCreateRegisteredUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String mobileNumber;

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @NotNull(message = "Space role is required")
    private MembershipRole spaceRole;

    /** Optional when spaceRole is not OWNER — existing active space to join. */
    private UUID spaceId;

    /** Optional display name for the new space when spaceRole is OWNER. */
    @Size(max = 255, message = "Space name must be at most 255 characters")
    private String spaceName;

    /** Required when spaceRole is OWNER. */
    private SpaceType spaceType;
}
