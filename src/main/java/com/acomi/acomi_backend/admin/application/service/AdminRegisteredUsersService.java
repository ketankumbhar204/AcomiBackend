package com.acomi.acomi_backend.admin.application.service;

import com.acomi.acomi_backend.admin.api.dto.request.AdminCreateRegisteredUserRequest;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUserSpaceResponse;
import com.acomi.acomi_backend.admin.api.dto.response.AdminRegisteredUsersSummaryResponse;
import com.acomi.acomi_backend.auth.application.service.AccountDeletionService;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.util.MobileNumberNormalizer;
import com.acomi.acomi_backend.member.application.service.InvitationProvisioner;
import com.acomi.acomi_backend.member.application.service.InvitationService;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.InvitationEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.api.dto.request.CreateSpaceRequest;
import com.acomi.acomi_backend.space.application.service.SpaceService;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import com.acomi.acomi_backend.user.domain.model.SystemRole;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private static final Set<MembershipRole> ALLOWED_SPACE_ROLES =
            EnumSet.allOf(MembershipRole.class);
    private static final List<MembershipRole> OWNER_OR_MANAGER =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private final UserRepository userRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final AccountDeletionService accountDeletionService;
    private final PasswordEncoder passwordEncoder;
    private final SpaceService spaceService;
    private final SpaceRepository spaceRepository;
    private final InvitationProvisioner invitationProvisioner;
    private final InvitationService invitationService;

    @Transactional(readOnly = true)
    public long countRegisteredUsers() {
        return userRepository.countByMobileVerifiedAtIsNotNullAndIsActiveTrueAndSystemRole(SystemRole.USER);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> list(Pageable pageable) {
        return list(null, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> search(String q, Pageable pageable) {
        return list(q, null, null, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdminRegisteredUserResponse> list(
            String q,
            String role,
            String onboarding,
            String spaceAssociation,
            Boolean verified,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        Pageable safe = safePage(pageable);
        String query = StringUtils.hasText(q) ? q.trim() : null;
        String queryDigits = digitsForSearch(query);
        String roleFilter = normalizeRole(role);
        Boolean fromOnboarding = parseHasSpaceFromOnboarding(onboarding);
        Boolean fromSpace = parseHasSpaceFromAssociation(spaceAssociation);
        if (fromOnboarding != null && fromSpace != null && !fromOnboarding.equals(fromSpace)) {
            return Page.empty(safe);
        }
        Boolean hasSpace = fromOnboarding != null ? fromOnboarding : fromSpace;
        // Integer sentinels avoid Hibernate/Postgres null-boolean binding bugs that return empty pages.
        int hasSpaceFlag = hasSpace == null ? -1 : (hasSpace ? 1 : 0);
        int verifiedFlag = verified == null ? -1 : (verified ? 1 : 0);
        LocalDateTime fromAt = from == null ? null : from.atStartOfDay();
        LocalDateTime toAt = to == null ? null : to.plusDays(1).atStartOfDay();

        boolean filtered = query != null
                || roleFilter != null
                || hasSpaceFlag != -1
                || verifiedFlag != -1
                || fromAt != null
                || toAt != null;

        Page<UserEntity> users = filtered
                ? userRepository.searchActiveUsersFiltered(
                        SystemRole.USER,
                        query,
                        queryDigits,
                        fromAt,
                        toAt,
                        hasSpaceFlag,
                        verifiedFlag,
                        roleFilter,
                        safe)
                : userRepository.findByIsActiveTrueAndSystemRole(SystemRole.USER, safe);
        return mapUsers(users);
    }

    private static String digitsForSearch(String query) {
        if (query == null) {
            return null;
        }
        String digits = query.replaceAll("\\D", "");
        return StringUtils.hasText(digits) ? digits : null;
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

    /**
     * Creates a real ACOMI USER for QA with {@code testUser=true}. Does not use OTP,
     * does not issue a JWT, and never elevates {@code systemRole} above USER.
     */
    @Transactional
    public AdminRegisteredUserResponse createTestUser(AdminCreateRegisteredUserRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Passwords do not match");
        }

        MembershipRole spaceRole = request.getSpaceRole();
        if (spaceRole == null || !ALLOWED_SPACE_ROLES.contains(spaceRole)) {
            throw new BusinessException("Space role must be one of OWNER, MANAGER, TENANT, CUSTOMER, STAFF");
        }

        validateSpaceRoleRequirements(request, spaceRole);

        String mobileNumber = MobileNumberNormalizer.normalize(request.getMobileNumber());
        if (userRepository.findByMobileNumberAndIsActiveTrue(mobileNumber).isPresent()) {
            throw new BusinessException("This mobile number is already registered.", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        UserEntity user;
        try {
            user = userRepository.save(UserEntity.builder()
                    .mobileNumber(mobileNumber)
                    .fullName(request.getFullName().trim())
                    .email(blankToNull(request.getEmail()) == null
                            ? null
                            : blankToNull(request.getEmail()).toLowerCase())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .mobileVerifiedAt(now)
                    .systemRole(SystemRole.USER)
                    .isActive(true)
                    .testUser(true)
                    .build());
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("This mobile number is already registered.", HttpStatus.CONFLICT);
        }

        if (spaceRole == MembershipRole.OWNER) {
            createOwnerSpaceForTestUser(user, request);
        } else {
            attachNonOwnerMembership(user, request.getSpaceId(), spaceRole);
        }

        List<SpaceMembershipEntity> memberships =
                spaceMembershipRepository.findActiveByUserIdsWithSpace(List.of(user.getId()));
        return toResponse(user, memberships);
    }

    private void validateSpaceRoleRequirements(
            AdminCreateRegisteredUserRequest request, MembershipRole spaceRole) {
        if (spaceRole == MembershipRole.OWNER) {
            if (request.getSpaceType() == null) {
                throw new BusinessException("Space type is required when creating an OWNER test user");
            }
            return;
        }
        // Non-OWNER: space is optional — omit spaceId to create a user with no membership.
        if (request.getSpaceId() == null) {
            return;
        }
        spaceRepository
                .findByIdAndIsActiveTrue(request.getSpaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", request.getSpaceId()));
    }

    /**
     * Real OWNER path: {@link SpaceService#createSpace} creates the space, ACTIVE OWNER
     * membership, and linked member record — same as product onboarding.
     */
    private void createOwnerSpaceForTestUser(UserEntity user, AdminCreateRegisteredUserRequest request) {
        String spaceName = StringUtils.hasText(request.getSpaceName())
                ? request.getSpaceName().trim()
                : defaultOwnerSpaceName(user.getFullName());
        CreateSpaceRequest createSpace = new CreateSpaceRequest();
        createSpace.setName(spaceName);
        createSpace.setType(request.getSpaceType());
        createSpace.setOwnerId(user.getId());
        createSpace.setDiscoverable(false);
        createSpace.setContactNumber(user.getMobileNumber());
        spaceService.createSpace(createSpace);
    }

    /**
     * Real non-OWNER path: pending invitation (as space owner would send) then
     * {@link InvitationService#acceptInvitation} so ACTIVE membership + member link match
     * normal invite acceptance.
     */
    private void attachNonOwnerMembership(UserEntity user, UUID spaceId, MembershipRole spaceRole) {
        if (spaceId == null) {
            return;
        }
        SpaceEntity space = spaceRepository
                .findByIdAndIsActiveTrue(spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Space", "id", spaceId));

        UserEntity invitedBy = space.getOwner();
        if (invitedBy == null || !invitedBy.isActive()) {
            throw new BusinessException(
                    "Space has no active owner eligible to invite members", HttpStatus.CONFLICT);
        }

        boolean canInvite = spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                invitedBy.getId(), space.getId(), OWNER_OR_MANAGER);
        if (!canInvite) {
            throw new BusinessException(
                    "Space owner is not an active OWNER or MANAGER of this space", HttpStatus.CONFLICT);
        }

        InvitationEntity invitation = invitationProvisioner
                .ensurePendingInvitation(space, invitedBy, user.getMobileNumber(), spaceRole)
                .orElseThrow(() -> new BusinessException(
                        "User already has an active membership in this space", HttpStatus.CONFLICT));

        invitationService.acceptInvitation(invitation.getId(), user.getId());
    }

    private static String defaultOwnerSpaceName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        if (trimmed.isEmpty()) {
            return "QA Test Space";
        }
        return "QA - " + trimmed;
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
