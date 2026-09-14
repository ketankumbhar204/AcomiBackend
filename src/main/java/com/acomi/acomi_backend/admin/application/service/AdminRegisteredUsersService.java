package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserSpaceResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUsersSummaryResponse;
import com.acomi.acomi_backend.auth.application.service.AccountDeletionService;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminRegisteredUsersService {

    public static final String ROLE_NOT_SELECTED = "NOT_SELECTED";
    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_OWNER_AND_MEMBER = "OWNER_AND_MEMBER";
    static final String ONBOARDING_INCOMPLETE = "INCOMPLETE";
    static final String ONBOARDING_COMPLETE = "COMPLETE";

    private final UserRepository userRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final AccountDeletionService accountDeletionService;

    @Transactional(readOnly = true)
    public long countRegisteredUsers() {
        return userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> list(Pageable pageable) {
        return list(null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> search(String q, Pageable pageable) {
        return list(q, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> list(
            String q,
            String role,
            String onboarding,
            String spaceAssociation,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        Pageable safe = safePage(pageable);
        String query = StringUtils.hasText(q) ? q.trim() : null;
        String roleFilter = normalizeRole(role);
        Boolean hasSpace = null;
        Boolean fromOnboarding = parseHasSpaceFromOnboarding(onboarding);
        Boolean fromSpace = parseHasSpaceFromAssociation(spaceAssociation);
        if (fromOnboarding != null && fromSpace != null && !fromOnboarding.equals(fromSpace)) {
            return Page.empty(safe);
        }
        hasSpace = fromOnboarding != null ? fromOnboarding : fromSpace;
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();

        boolean filtered = query != null
                || roleFilter != null
                || hasSpace != null
                || fromAt != null
                || toAt != null;

        Page<UserEntity> users = filtered
                ? userRepository.searchVerifiedUsersFiltered(
                        SystemRole.USER, query, fromAt, toAt, hasSpace, roleFilter, safe)
                : userRepository.findByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(
                        SystemRole.USER, safe);
        return mapUsers(users);
    }

    @Transactional(readOnly = true)
    public AdminRegisteredUsersSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last30From = now.minusDays(30);
        LocalDateTime prev30From = now.minusDays(60);

        long total = userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
        long allActive = userRepository.countByIsActiveTrueAndSystemRole(SystemRole.USER);
        long verified = total;
        long newNow = userRepository.countVerifiedUsersRegisteredBetween(SystemRole.USER, last30From, now);
        long newPrev = userRepository.countVerifiedUsersRegisteredBetween(SystemRole.USER, prev30From, last30From);
        long withSpace = userRepository.countVerifiedUsersWithActiveSpace(SystemRole.USER);
        long withSpaceNow =
                userRepository.countVerifiedUsersWithActiveSpaceRegisteredBetween(SystemRole.USER, last30From, now);
        long withSpacePrev = userRepository.countVerifiedUsersWithActiveSpaceRegisteredBetween(
                SystemRole.USER, prev30From, last30From);
        // Lifetime totals: compare new registrations in last 30d vs prev 30d as trend signal.
        return AdminRegisteredUsersSummaryResponse.builder()
                .totalUsers(allActive)
                .verifiedUsers(verified)
                .newUsersLast30Days(newNow)
                .withSpaceAssociation(withSpace)
                .totalUsersDeltaPercent(deltaPercent(newNow, newPrev))
                .verifiedUsersDeltaPercent(deltaPercent(newNow, newPrev))
                .newUsersDeltaPercent(deltaPercent(newNow, newPrev))
                .withSpaceDeltaPercent(deltaPercent(withSpaceNow, withSpacePrev))
                .build();
    }

    @Transactional(readOnly = true)
    public AdminRegisteredUserResponse getById(UUID id) {
        UserEntity user = userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        List<SpaceMembershipEntity> memberships =
                spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(id));
        return toResponse(user, memberships);
    }

    @Transactional
    public void delete(UUID id) {
        accountDeletionService.deleteAccountByAdmin(id);
    }

    @Transactional
    public AdminRegisteredUserResponse setTestUser(UUID id, boolean testUser) {
        UserEntity user = userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        if (!user.isActive() || user.getSystemRole() != SystemRole.USER) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        user.setTestUser(testUser);
        userRepository.save(user);
        List<SpaceMembershipEntity> memberships =
                spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(id));
        return toResponse(user, memberships);
    }

    /** Bulk selected-role derivation for dashboard breakdown (one membership query). */
    @Transactional(readOnly = true)
    public Map<UUID, String> selectedRolesForUsers(List<UUID> userIds) {
        Map<UUID, String> roles = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            roles.put(userId, ROLE_NOT_SELECTED);
        }
        if (userIds.isEmpty()) {
            return roles;
        }
        Map<UUID, Boolean> hasOwner = new LinkedHashMap<>();
        Map<UUID, Boolean> hasMember = new LinkedHashMap<>();
        for (UUID userId : userIds) {
            hasOwner.put(userId, false);
            hasMember.put(userId, false);
        }
        for (SpaceMembershipEntity membership : spaceMembershipRepository.findActiveByUserIdsWithSpace(userIds)) {
            UUID userId = membership.getUser().getId();
            if (membership.getRole() == MembershipRole.OWNER) {
                hasOwner.put(userId, true);
            } else {
                hasMember.put(userId, true);
            }
        }
        for (UUID userId : userIds) {
            roles.put(
                    userId,
                    selectedRole(
                            Boolean.TRUE.equals(hasOwner.get(userId)),
                            Boolean.TRUE.equals(hasMember.get(userId))));
        }
        return roles;
    }

    private Page<AdminRegisteredUserResponse> mapUsers(Page<UserEntity> users) {
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
                .email(blankToNull(user.getEmail()))
                .mobileVerified(user.getMobileVerifiedAt() != null)
                .mobileVerifiedAt(user.getMobileVerifiedAt())
                .registeredAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .selectedRole(selectedRole(hasOwner, hasMember))
                .onboardingStatus(memberships.isEmpty() ? ONBOARDING_INCOMPLETE : ONBOARDING_COMPLETE)
                .profileCompleted(user.isProfileCompleted())
                .spaces(List.copyOf(spaces))
                .systemRole(user.getSystemRole() == null ? null : user.getSystemRole().name())
                .active(user.isActive())
                .testUser(user.isTestUser())
                .build();
    }

    public static String selectedRole(boolean hasOwner, boolean hasMember) {
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

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String value = role.trim().toUpperCase();
        return switch (value) {
            case ROLE_OWNER, ROLE_MEMBER, ROLE_OWNER_AND_MEMBER, ROLE_NOT_SELECTED -> value;
            default -> null;
        };
    }

    private static Boolean parseHasSpaceFromOnboarding(String onboarding) {
        if (!StringUtils.hasText(onboarding)) {
            return null;
        }
        String value = onboarding.trim().toUpperCase();
        if (ONBOARDING_COMPLETE.equals(value)) {
            return true;
        }
        if (ONBOARDING_INCOMPLETE.equals(value)) {
            return false;
        }
        return null;
    }

    private static Boolean parseHasSpaceFromAssociation(String spaceAssociation) {
        if (!StringUtils.hasText(spaceAssociation)) {
            return null;
        }
        String value = spaceAssociation.trim().toUpperCase();
        if ("WITH_SPACE".equals(value) || "WITH".equals(value)) {
            return true;
        }
        if ("WITHOUT_SPACE".equals(value) || "WITHOUT".equals(value)) {
            return false;
        }
        return null;
    }

    private static Double deltaPercent(long current, long previous) {
        if (previous == 0L) {
            if (current == 0L) {
                return 0.0;
            }
            return 100.0;
        }
        return Math.round(((current - previous) * 1000.0) / previous) / 10.0;
    }

    private static Pageable safePage(Pageable pageable) {
        int page = pageable != null ? Math.max(pageable.getPageNumber(), 0) : 0;
        int size = pageable != null ? Math.max(pageable.getPageSize(), 1) : 20;
        size = Math.min(size, 100);
        Sort sort = pageable != null && pageable.getSort().isSorted()
                ? pageable.getSort()
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return PageRequest.of(page, size, sort);
    }
}
