package com.acomi.acomi_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.api.dto.response.AllocationTargetSearchResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationProfileResolver;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.projection.AllocationTargetSearchRow;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.AllocationTargetSearchRepository;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.member.application.service.SpaceMembershipResolver;
import com.acomi.acomi_backend.member.domain.model.MembershipRole;
import com.acomi.acomi_backend.member.domain.model.MembershipStatus;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AllocationTargetSearchServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpaceMembershipRepository spaceMembershipRepository;

    @Spy
    private AccommodationProfileResolver profileResolver = new AccommodationProfileResolver();

    @Mock
    private AllocationTargetSearchRepository searchRepository;

    private AccommodationAccessService accessService;
    private AllocationTargetSearchService searchService;

    private UUID spaceId;
    private UUID callerId;

    @BeforeEach
    void setUp() {
        SpaceMembershipResolver membershipResolver = new SpaceMembershipResolver(spaceMembershipRepository);
        accessService = new AccommodationAccessService(spaceRepository, membershipResolver, profileResolver);
        searchService = new AllocationTargetSearchService(accessService, searchRepository);
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
    }

    @Test
    void searchAllocationTargets_defaultsToBedForPg() {
        stubPgSpaceAccess();
        UUID bedId = UUID.randomUUID();
        AllocationTargetSearchRow row = new AllocationTargetSearchRow(
                AllocationTargetType.BED,
                bedId,
                UUID.randomUUID(),
                "Building A",
                "A",
                UUID.randomUUID(),
                "Floor 1",
                null,
                null,
                null,
                UUID.randomUUID(),
                "Room 101",
                "101",
                bedId,
                "Bed A",
                "A",
                AccommodationStatus.AVAILABLE,
                new BigDecimal("8000"),
                new BigDecimal("1000"));
        Pageable pageable = PageRequest.of(0, 20);
        when(searchRepository.searchBedTargets(
                        eq(spaceId), eq("101"), eq(null), eq(null), eq(null), eq(null), eq(true), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PagedResponse<AllocationTargetSearchResponse> response = searchService.searchAllocationTargets(
                spaceId, callerId, "101", null, null, null, null, null, true, pageable);

        assertThat(response.getContent()).hasSize(1);
        AllocationTargetSearchResponse item = response.getContent().get(0);
        assertThat(item.getTargetType()).isEqualTo(AllocationTargetType.BED);
        assertThat(item.getDisplayPath()).contains("Building A").contains("Room 101").contains("Bed A");
        assertThat(item.isSelectable()).isTrue();
    }

    @Test
    void searchAllocationTargets_defaultsToUnitForRental() {
        SpaceEntity space = SpaceEntity.builder().name("Rental").type(SpaceType.RENTAL).isActive(true).build();
        space.setId(spaceId);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        AccommodationAccessTestSupport.stubMembership(
                spaceMembershipRepository, callerId, spaceId, space, MembershipRole.MANAGER);

        UUID unitId = UUID.randomUUID();
        AllocationTargetSearchRow row = new AllocationTargetSearchRow(
                AllocationTargetType.UNIT,
                unitId,
                UUID.randomUUID(),
                "Block A",
                "A",
                null,
                null,
                unitId,
                "Flat 101",
                "101",
                null,
                null,
                null,
                null,
                null,
                null,
                AccommodationStatus.AVAILABLE,
                new BigDecimal("20000"),
                BigDecimal.ZERO);
        Pageable pageable = PageRequest.of(0, 20);
        when(searchRepository.searchUnitTargets(
                        eq(spaceId), eq(null), eq(null), eq(null), eq(null), eq(false), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PagedResponse<AllocationTargetSearchResponse> response = searchService.searchAllocationTargets(
                spaceId, callerId, null, null, null, null, null, null, false, pageable);

        verify(searchRepository)
                .searchUnitTargets(eq(spaceId), eq(null), eq(null), eq(null), eq(null), eq(false), eq(pageable));
        assertThat(response.getContent().get(0).getTargetType()).isEqualTo(AllocationTargetType.UNIT);
        assertThat(response.getContent().get(0).getDisplayPath()).isEqualTo("Block A · Flat 101");
    }

    private void stubPgSpaceAccess() {
        SpaceEntity space = SpaceEntity.builder().name("PG").type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);
        when(spaceRepository.findByIdAndIsActiveTrue(spaceId)).thenReturn(Optional.of(space));
        UserEntity user = UserEntity.builder().fullName("Manager").mobileNumber("9000000007").build();
        user.setId(callerId);
        SpaceMembershipEntity membership = SpaceMembershipEntity.builder()
                .user(user)
                .space(space)
                .role(MembershipRole.MANAGER)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(spaceMembershipRepository.findMembershipByUserAndSpace(callerId, spaceId))
                .thenReturn(Optional.of(membership));
    }
}
