package com.acomi.acomi_backend.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.member.api.dto.response.MemberDirectoryPersonResponse;
import com.acomi.acomi_backend.member.domain.model.MemberStatus;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MemberDirectoryServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @InjectMocks
    private MemberDirectoryService memberDirectoryService;

    private UUID callerId;
    private UUID spacePgId;
    private UUID spaceMessId;
    private UUID otherOwnerSpaceId;

    @BeforeEach
    void setUp() {
        callerId = UUID.randomUUID();
        spacePgId = UUID.randomUUID();
        spaceMessId = UUID.randomUUID();
        otherOwnerSpaceId = UUID.randomUUID();
    }

    @Test
    void emptyManagedPortfolio_returnsEmptyPage() {
        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of());

        PagedResponse<MemberDirectoryPersonResponse> page = memberDirectoryService.searchDirectory(
                callerId, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void unauthorizedSpaceFilter_forbidden() {
        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of(spacePgId));

        assertThatThrownBy(() -> memberDirectoryService.searchDirectory(
                        callerId, null, otherOwnerSpaceId, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void dedupesByUserId_acrossSpaces() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "Rahul Sharma", "9876543210");
        MemberEntity pgMember = member(UUID.randomUUID(), "Rahul Sharma", "9876543210",
                MembershipRole.TENANT, space(spacePgId, "Sunrise PG", SpaceType.PG), user);
        MemberEntity messMember = member(UUID.randomUUID(), "Rahul Sharma", "9876543210",
                MembershipRole.CUSTOMER, space(spaceMessId, "ABC Mess", SpaceType.MESS), user);

        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of(spacePgId, spaceMessId));
        when(memberRepository.searchDirectoryMembers(any(), any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(pgMember, messMember));

        PagedResponse<MemberDirectoryPersonResponse> page = memberDirectoryService.searchDirectory(
                callerId, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        MemberDirectoryPersonResponse person = page.getContent().get(0);
        assertThat(person.getUserId()).isEqualTo(userId);
        assertThat(person.getDirectoryId()).isEqualTo("u:" + userId);
        assertThat(person.getAssociations()).hasSize(2);
        assertThat(person.getAssociations())
                .extracting(a -> a.getSpaceName())
                .containsExactlyInAnyOrder("ABC Mess", "Sunrise PG");
    }

    @Test
    void fallsBackToMobile_whenNoUser() {
        MemberEntity a = member(UUID.randomUUID(), "Amit", "9123456780",
                MembershipRole.CUSTOMER, space(spaceMessId, "ABC Mess", SpaceType.MESS), null);
        MemberEntity b = member(UUID.randomUUID(), "Amit Kumar", "9123456780",
                MembershipRole.TENANT, space(spacePgId, "Sunrise PG", SpaceType.PG), null);

        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of(spacePgId, spaceMessId));
        when(memberRepository.searchDirectoryMembers(any(), any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(a, b));

        PagedResponse<MemberDirectoryPersonResponse> page = memberDirectoryService.searchDirectory(
                callerId, null, null, null, null, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getDirectoryId()).isEqualTo("m:9123456780");
        assertThat(page.getContent().get(0).getUserId()).isNull();
        assertThat(page.getContent().get(0).getAssociations()).hasSize(2);
    }

    @Test
    void attachesUnlinkedMobileToExistingUserBucket() {
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "Rahul", "9876543210");
        MemberEntity linked = member(UUID.randomUUID(), "Rahul", "9876543210",
                MembershipRole.TENANT, space(spacePgId, "Sunrise PG", SpaceType.PG), user);
        MemberEntity unlinked = member(UUID.randomUUID(), "Rahul", "9876543210",
                MembershipRole.CUSTOMER, space(spaceMessId, "ABC Mess", SpaceType.MESS), null);

        List<MemberDirectoryPersonResponse> people =
                memberDirectoryService.groupPeople(List.of(linked, unlinked));

        assertThat(people).hasSize(1);
        assertThat(people.get(0).getUserId()).isEqualTo(userId);
        assertThat(people.get(0).getAssociations()).hasSize(2);
    }

    @Test
    void doesNotMergeDistinctUserIds_evenWithSameMobile() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        MemberEntity a = member(UUID.randomUUID(), "Person A", "9000000001",
                MembershipRole.TENANT, space(spacePgId, "Sunrise PG", SpaceType.PG),
                user(userA, "Person A", "9000000001"));
        MemberEntity b = member(UUID.randomUUID(), "Person B", "9000000001",
                MembershipRole.CUSTOMER, space(spaceMessId, "ABC Mess", SpaceType.MESS),
                user(userB, "Person B", "9000000001"));

        List<MemberDirectoryPersonResponse> people = memberDirectoryService.groupPeople(List.of(a, b));

        assertThat(people).hasSize(2);
        assertThat(people).extracting(MemberDirectoryPersonResponse::getUserId)
                .containsExactlyInAnyOrder(userA, userB);
    }

    @Test
    void paginatesUniquePeople() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        UUID u3 = UUID.randomUUID();
        List<MemberEntity> rows = List.of(
                member(UUID.randomUUID(), "Alice", "9111111111", MembershipRole.TENANT,
                        space(spacePgId, "PG", SpaceType.PG), user(u1, "Alice", "9111111111")),
                member(UUID.randomUUID(), "Bob", "9222222222", MembershipRole.TENANT,
                        space(spacePgId, "PG", SpaceType.PG), user(u2, "Bob", "9222222222")),
                member(UUID.randomUUID(), "Carol", "9333333333", MembershipRole.CUSTOMER,
                        space(spaceMessId, "Mess", SpaceType.MESS), user(u3, "Carol", "9333333333")));

        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of(spacePgId, spaceMessId));
        when(memberRepository.searchDirectoryMembers(any(), any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(rows);

        PagedResponse<MemberDirectoryPersonResponse> page = memberDirectoryService.searchDirectory(
                callerId, null, null, null, null, PageRequest.of(1, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getDisplayName()).isEqualTo("Carol");
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void passesSearchAndFiltersToRepository() {
        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of(spacePgId));
        when(memberRepository.searchDirectoryMembers(
                        any(), any(), eq("rahul"), isNull(), eq(spacePgId), eq(SpaceType.PG),
                        eq(MemberStatus.ACTIVE)))
                .thenReturn(List.of());

        memberDirectoryService.searchDirectory(
                callerId, "rahul", spacePgId, SpaceType.PG, MemberStatus.ACTIVE, PageRequest.of(0, 10));

        verify(memberRepository).searchDirectoryMembers(
                any(), any(), eq("rahul"), isNull(), eq(spacePgId), eq(SpaceType.PG),
                eq(MemberStatus.ACTIVE));
    }

    @Test
    void paginationAcrossManyUniquePeople_hasNoDuplicatesOrSkips() {
        List<MemberEntity> rows = new java.util.ArrayList<>();
        java.util.Set<String> expectedIds = new java.util.HashSet<>();
        // 60 unique users; 40 of them also appear in a second space → 100 member rows.
        for (int i = 0; i < 60; i++) {
            UUID userId = UUID.randomUUID();
            String name = String.format("Person %02d", i);
            String mobile = String.format("9%09d", i);
            UserEntity user = user(userId, name, mobile);
            expectedIds.add("u:" + userId);
            rows.add(member(UUID.randomUUID(), name, mobile, MembershipRole.TENANT,
                    space(spacePgId, "Sunrise PG", SpaceType.PG), user));
            if (i < 40) {
                rows.add(member(UUID.randomUUID(), name, mobile, MembershipRole.CUSTOMER,
                        space(spaceMessId, "ABC Mess", SpaceType.MESS), user));
            }
        }

        when(spaceMembershipRepository.findActiveSpaceIdsForRoles(eq(callerId), any()))
                .thenReturn(List.of(spacePgId, spaceMessId));
        when(memberRepository.searchDirectoryMembers(any(), any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(rows);

        java.util.Set<String> seen = new java.util.HashSet<>();
        int pageSize = 20;
        int expectedPages = 3;
        for (int page = 0; page < expectedPages; page++) {
            PagedResponse<MemberDirectoryPersonResponse> result =
                    memberDirectoryService.searchDirectory(
                            callerId, null, null, null, null, PageRequest.of(page, pageSize));
            assertThat(result.getTotalElements()).isEqualTo(60);
            assertThat(result.getTotalPages()).isEqualTo(expectedPages);
            for (MemberDirectoryPersonResponse person : result.getContent()) {
                assertThat(seen.add(person.getDirectoryId()))
                        .as("duplicate directoryId %s on page %s", person.getDirectoryId(), page)
                        .isTrue();
            }
        }
        assertThat(seen).isEqualTo(expectedIds);
    }

    @Test
    void ambiguousSharedMobile_doesNotAttachUnlinkedToEitherUser() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        MemberEntity a = member(UUID.randomUUID(), "Person A", "9000000001",
                MembershipRole.TENANT, space(spacePgId, "Sunrise PG", SpaceType.PG),
                user(userA, "Person A", "9000000001"));
        MemberEntity b = member(UUID.randomUUID(), "Person B", "9000000001",
                MembershipRole.CUSTOMER, space(spaceMessId, "ABC Mess", SpaceType.MESS),
                user(userB, "Person B", "9000000001"));
        UUID orphanId = UUID.randomUUID();
        MemberEntity unlinked = member(orphanId, "Unlinked", "9000000001",
                MembershipRole.TENANT, space(spacePgId, "Sunrise PG", SpaceType.PG), null);

        List<MemberDirectoryPersonResponse> people =
                memberDirectoryService.groupPeople(List.of(a, b, unlinked));

        assertThat(people).hasSize(3);
        assertThat(people).extracting(MemberDirectoryPersonResponse::getDirectoryId)
                .containsExactlyInAnyOrder("u:" + userA, "u:" + userB, "o:" + orphanId);
        assertThat(people.stream().filter(p -> ("o:" + orphanId).equals(p.getDirectoryId())).findFirst())
                .get()
                .extracting(MemberDirectoryPersonResponse::getAssociations)
                .asList()
                .hasSize(1);
    }

    @Test
    void memberWithoutUserOrMobile_appearsAsOrphan() {
        UUID memberId = UUID.randomUUID();
        MemberEntity orphan = member(memberId, "No Contact", "",
                MembershipRole.STAFF, space(spacePgId, "Sunrise PG", SpaceType.PG), null);

        List<MemberDirectoryPersonResponse> people = memberDirectoryService.groupPeople(List.of(orphan));

        assertThat(people).hasSize(1);
        assertThat(people.get(0).getDirectoryId()).isEqualTo("o:" + memberId);
        assertThat(people.get(0).getDisplayName()).isEqualTo("No Contact");
    }

    @Test
    void clampPageable_capsOversizedRequests() {
        Pageable clamped = MemberDirectoryService.clampPageable(PageRequest.of(0, 500));
        assertThat(clamped.getPageSize()).isEqualTo(MemberDirectoryService.MAX_PAGE_SIZE);
    }

    private static UserEntity user(UUID id, String name, String mobile) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setFullName(name);
        user.setMobileNumber(mobile);
        return user;
    }

    private static SpaceEntity space(UUID id, String name, SpaceType type) {
        SpaceEntity space = new SpaceEntity();
        space.setId(id);
        space.setName(name);
        space.setType(type);
        space.setActive(true);
        return space;
    }

    private static MemberEntity member(
            UUID id,
            String name,
            String mobile,
            MembershipRole role,
            SpaceEntity space,
            UserEntity user) {
        MemberEntity member = new MemberEntity();
        member.setId(id);
        member.setFullName(name);
        member.setMobileNumber(mobile);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setOccupancyStatus(MemberOccupancyStatus.VACATED);
        member.setActive(true);
        member.setSpace(space);
        member.setUser(user);
        return member;
    }
}
