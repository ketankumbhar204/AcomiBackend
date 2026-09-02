package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserSpaceResponse;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminRegisteredUsersService {

    static final String ROLE_NOT_SELECTED = "NOT_SELECTED";
    static final String ROLE_OWNER = "OWNER";
    static final String ROLE_MEMBER = "MEMBER";
    static final String ROLE_OWNER_AND_MEMBER = "OWNER_AND_MEMBER";
    static final String ONBOARDING_INCOMPLETE = "INCOMPLETE";
    static final String ONBOARDING_COMPLETE = "COMPLETE";

    private final UserRepository userRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;

    @Transactional(readOnly = true)
    public long countRegisteredUsers() {
        return userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> list(Pageable pageable) {
        Page<UserEntity> users = userRepository.findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
                SystemRole.USER, pageable);
        List<UserEntity> content = users.getContent();
        if (content.isEmpty()) {
            return users.map(user -> toResponse(user, List.of()));
        }

        List<UUID> userIds = content.stream().map(UserEntity::getId).toList();
        Map<UUID, List<SpaceMembershipEntity>> membershipsByUser = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            membershipsByUser.put(userId, new ArrayList<>());
        }
        for (SpaceMembershipEntity membership : spaceMembershipRepository.findActiveByUserIdsWithSpace(userIds)) {
            membershipsByUser
                    .computeIfAbsent(membership.getUser().getId(), ignored -> new ArrayList<>())
                    .add(membership);
        }

        return users.map(user -> toResponse(user, membershipsByUser.getOrDefault(user.getId(), List.of())));
    }

    private AdminRegisteredUserResponse toResponse(UserEntity user, List<SpaceMembershipEntity> memberships) {
        boolean hasOwner = false;
        boolean hasMember = false;
        List<AdminRegisteredUserSpaceResponse> spaces = new ArrayList<>();
        for (SpaceMembershipEntity membership : memberships) {
            if (membership.getRole() == MembershipRole.OWNER) {
                hasOwner = true;
            } else {
                hasMember = true;
            }
            spaces.add(AdminRegisteredUserSpaceResponse.builder()
                    .id(membership.getSpace().getId())
                    .name(membership.getSpace().getName())
                    .type(membership.getSpace().getType())
                    .membershipRole(membership.getRole())
                    .build());
        }

        return AdminRegisteredUserResponse.builder()
                .id(user.getId())
                .fullName(displayName(user.getFullName()))
                .mobileNumber(user.getMobileNumber())
                .mobileVerified(user.getMobileVerifiedAt() != null)
                .mobileVerifiedAt(user.getMobileVerifiedAt())
                .registeredAt(user.getCreatedAt())
                .selectedRole(selectedRole(hasOwner, hasMember))
                .onboardingStatus(memberships.isEmpty() ? ONBOARDING_INCOMPLETE : ONBOARDING_COMPLETE)
                .profileCompleted(user.isProfileCompleted())
                .spaces(List.copyOf(spaces))
                .build();
    }

    static String selectedRole(boolean hasOwner, boolean hasMember) {
        if (hasOwner && hasMember) {
            return ROLE_OWNER_AND_MEMBER;
        }
        if (hasOwner) {
            return ROLE_OWNER;
        }
        if (hasMember) {
            return ROLE_MEMBER;
        }
        return ROLE_NOT_SELECTED;
    }

    static String displayName(String fullName) {
        if (!StringUtils.hasText(fullName)) {
            return null;
        }
        String trimmed = fullName.trim();
        if ("user".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
