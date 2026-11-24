package com.acomi.acomi_backend.member.application.service;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.member.api.dto.response.MemberDirectoryAssociationResponse;
import com.acomi.acomi_backend.member.api.dto.response.MemberDirectoryPersonResponse;
import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Owner/manager portfolio directory: unique people across managed spaces.
 * Discovery only — does not create space associations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberDirectoryService {

    private static final List<MembershipRole> OWNER_OR_MANAGER =
            List.of(MembershipRole.OWNER, MembershipRole.MANAGER);

    private static final Set<MembershipRole> DIRECTORY_ROLES =
            EnumSet.of(MembershipRole.TENANT, MembershipRole.CUSTOMER, MembershipRole.STAFF);

    /** Hard cap for client page size — directory still groups the full filtered set in memory. */
    static final int MAX_PAGE_SIZE = 100;

    /**
     * Soft threshold: when matched member rows exceed this, log a warning that SQL-level identity
     * pagination should be considered for this portfolio.
     */
    static final int IN_MEMORY_ROW_WARN_THRESHOLD = 5_000;

    private final MemberRepository memberRepository;
    private final SpaceMembershipRepository spaceMembershipRepository;

    @Transactional(readOnly = true)
    public PagedResponse<MemberDirectoryPersonResponse> searchDirectory(
            UUID callerId,
            String search,
            UUID spaceId,
            SpaceType spaceType,
            MemberStatus status,
            Pageable pageable) {
        Pageable safePageable = clampPageable(pageable);
        List<UUID> managedSpaceIds =
                spaceMembershipRepository.findActiveSpaceIdsForRoles(callerId, OWNER_OR_MANAGER);
        if (managedSpaceIds.isEmpty()) {
            return PagedResponse.from(new PageImpl<>(List.of(), safePageable, 0));
        }

        if (spaceId != null && !managedSpaceIds.contains(spaceId)) {
            throw new BusinessException(
                    "You are not authorized to manage this space", HttpStatus.FORBIDDEN);
        }

        String normalizedSearch = normalizeSearch(search);
        List<MemberEntity> rows = memberRepository.searchDirectoryMembers(
                managedSpaceIds,
                DIRECTORY_ROLES,
                normalizedSearch,
                mobileSearchDigits(normalizedSearch),
                spaceId,
                spaceType,
                status);

        if (rows.size() >= IN_MEMORY_ROW_WARN_THRESHOLD) {
            log.warn(
                    "Member directory large portfolio: callerId={}, matchedMembers={}, managedSpaces={}. "
                            + "In-memory unique-person pagination may need SQL-level identity paging.",
                    callerId,
                    rows.size(),
                    managedSpaceIds.size());
        }

        List<MemberDirectoryPersonResponse> people = groupPeople(rows);
        people.sort(Comparator
                .comparing((MemberDirectoryPersonResponse p) ->
                        p.getDisplayName() == null ? "" : p.getDisplayName().toLowerCase(Locale.ROOT))
                .thenComparing(p -> p.getDirectoryId() == null ? "" : p.getDirectoryId()));

        int total = people.size();
        int from = Math.min((int) safePageable.getOffset(), total);
        int to = Math.min(from + safePageable.getPageSize(), total);
        List<MemberDirectoryPersonResponse> pageContent = people.subList(from, to);

        log.info(
                "Member directory: callerId={}, managedSpaces={}, matchedMembers={}, uniquePeople={}, page={}/{}",
                callerId,
                managedSpaceIds.size(),
                rows.size(),
                total,
                safePageable.getPageNumber(),
                safePageable.getPageSize());

        return PagedResponse.from(new PageImpl<>(pageContent, safePageable, total));
    }

    static Pageable clampPageable(Pageable pageable) {
        int size = pageable.getPageSize();
        if (size < 1) {
            size = 20;
        } else if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        if (size == pageable.getPageSize()) {
            return pageable;
        }
        return PageRequest.of(Math.max(pageable.getPageNumber(), 0), size);
    }

    /**
     * Prefer user_id identity. Fallback to normalized mobile for unlinked members.
     * Mobile-only rows that share a mobile with exactly one user-linked person are attached to that
     * person. Distinct user_ids are never merged even if mobiles match. When multiple user identities
     * share a mobile, unlinked rows stay as per-member orphans (never silently attached).
     */
    List<MemberDirectoryPersonResponse> groupPeople(List<MemberEntity> rows) {
        Map<UUID, DirectoryBucket> byUserId = new LinkedHashMap<>();
        Map<String, DirectoryBucket> byMobile = new LinkedHashMap<>();
        Map<UUID, DirectoryBucket> byOrphanMemberId = new LinkedHashMap<>();
        Map<String, UUID> mobileToUserId = new HashMap<>();
        Set<String> ambiguousMobiles = new HashSet<>();

        for (MemberEntity member : rows) {
            if (member.getUser() != null && member.getUser().getId() != null) {
                UUID userId = member.getUser().getId();
                DirectoryBucket bucket =
                        byUserId.computeIfAbsent(userId, id -> DirectoryBucket.forUser(member.getUser()));
                bucket.add(member);
                String mobileKey = mobileKey(member.getMobileNumber());
                if (mobileKey != null) {
                    UUID existing = mobileToUserId.putIfAbsent(mobileKey, userId);
                    if (existing != null && !existing.equals(userId)) {
                        ambiguousMobiles.add(mobileKey);
                    }
                }
            }
        }

        for (MemberEntity member : rows) {
            if (member.getUser() != null && member.getUser().getId() != null) {
                continue;
            }
            String mobileKey = mobileKey(member.getMobileNumber());
            if (mobileKey == null) {
                byOrphanMemberId
                        .computeIfAbsent(member.getId(), DirectoryBucket::forOrphanMember)
                        .add(member);
                continue;
            }
            if (ambiguousMobiles.contains(mobileKey)) {
                byOrphanMemberId
                        .computeIfAbsent(member.getId(), DirectoryBucket::forOrphanMember)
                        .add(member);
                continue;
            }
            UUID linkedUserId = mobileToUserId.get(mobileKey);
            if (linkedUserId != null) {
                byUserId.get(linkedUserId).add(member);
            } else {
                byMobile.computeIfAbsent(mobileKey, DirectoryBucket::forMobile).add(member);
            }
        }

        List<MemberDirectoryPersonResponse> result = new ArrayList<>();
        byUserId.values().forEach(bucket -> result.add(bucket.toResponse()));
        byMobile.values().forEach(bucket -> result.add(bucket.toResponse()));
        byOrphanMemberId.values().forEach(bucket -> result.add(bucket.toResponse()));
        return result;
    }

    private static String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String mobileSearchDigits(String search) {
        if (search == null) {
            return null;
        }
        String digits = search.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.length() >= 4 ? digits : null;
    }

    private static String mobileKey(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return null;
        }
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() >= 10) {
            return digits.substring(digits.length() - 10);
        }
        return digits.isEmpty() ? null : digits;
    }

    private static final class DirectoryBucket {
        private final String directoryId;
        private final UUID userId;
        private String displayName;
        private String mobileNumber;
        private String profilePhotoUrl;
        private final Map<UUID, MemberDirectoryAssociationResponse> associations = new LinkedHashMap<>();

        private DirectoryBucket(String directoryId, UUID userId, String displayName, String mobileNumber,
                String profilePhotoUrl) {
            this.directoryId = directoryId;
            this.userId = userId;
            this.displayName = displayName;
            this.mobileNumber = mobileNumber;
            this.profilePhotoUrl = profilePhotoUrl;
        }

        static DirectoryBucket forUser(UserEntity user) {
            return new DirectoryBucket(
                    "u:" + user.getId(),
                    user.getId(),
                    user.getFullName(),
                    user.getMobileNumber(),
                    user.getProfilePhotoUrl());
        }

        static DirectoryBucket forMobile(String mobileKey) {
            return new DirectoryBucket("m:" + mobileKey, null, null, mobileKey, null);
        }

        /** One-off row when identity cannot be safely keyed by user or unambiguous mobile. */
        static DirectoryBucket forOrphanMember(UUID memberId) {
            return new DirectoryBucket("o:" + memberId, null, null, null, null);
        }

        void add(MemberEntity member) {
            if (!StringUtils.hasText(displayName) && StringUtils.hasText(member.getFullName())) {
                displayName = member.getFullName();
            }
            if (!StringUtils.hasText(mobileNumber) && StringUtils.hasText(member.getMobileNumber())) {
                mobileNumber = member.getMobileNumber();
            }
            if (userId != null
                    && !StringUtils.hasText(profilePhotoUrl)
                    && member.getUser() != null
                    && StringUtils.hasText(member.getUser().getProfilePhotoUrl())) {
                profilePhotoUrl = member.getUser().getProfilePhotoUrl();
            }
            associations.putIfAbsent(member.getId(), MemberDirectoryAssociationResponse.from(member));
        }

        MemberDirectoryPersonResponse toResponse() {
            List<MemberDirectoryAssociationResponse> sorted = associations.values().stream()
                    .sorted(Comparator.comparing(
                            a -> a.getSpaceName() == null ? "" : a.getSpaceName().toLowerCase(Locale.ROOT)))
                    .toList();
            return MemberDirectoryPersonResponse.builder()
                    .directoryId(directoryId)
                    .userId(userId)
                    .displayName(Objects.requireNonNullElse(displayName, ""))
                    .mobileNumber(mobileNumber)
                    .profilePhotoUrl(profilePhotoUrl)
                    .associations(sorted)
                    .build();
        }
    }
}
