package com.amico.amico_backend.meal.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.occupancy.domain.model.OccupancyStatus;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealOccupancyPolicyTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    private MealOccupancyPolicy policy;

    private UUID spaceId;
    private UUID memberId;
    private SpaceEntity pgSpace;

    @BeforeEach
    void setUp() {
        policy = new MealOccupancyPolicy(occupancyRepository);
        spaceId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        pgSpace = SpaceEntity.builder().type(SpaceType.PG).name("Test PG").build();
        pgSpace.setId(spaceId);
    }

    @Test
    void requiresActiveOccupancy_falseForMess() {
        assertThat(policy.requiresActiveOccupancy(SpaceType.MESS)).isFalse();
    }

    @Test
    void requiresActiveOccupancy_trueForPg() {
        assertThat(policy.requiresActiveOccupancy(SpaceType.PG)).isTrue();
    }

    @Test
    void hasOccupancyOnDate_trueForMessWithoutLookup() {
        SpaceEntity messSpace = SpaceEntity.builder().type(SpaceType.MESS).build();
        assertThat(policy.hasOccupancyOnDate(messSpace, memberId, LocalDate.now())).isTrue();
    }

    @Test
    void hasOccupancyOnDate_falseWhenPgTenantHasNoActiveOccupancy() {
        when(occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId))
                .thenReturn(Optional.empty());

        assertThat(policy.hasOccupancyOnDate(pgSpace, memberId, LocalDate.now())).isFalse();
    }

    @Test
    void hasOccupancyOnDate_trueWhenPgTenantHasActiveOccupancyOnDate() {
        MemberEntity member = MemberEntity.builder().build();
        member.setId(memberId);
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(pgSpace)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.now().minusDays(1))
                .build();
        when(occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId))
                .thenReturn(Optional.of(occupancy));

        assertThat(policy.hasOccupancyOnDate(pgSpace, memberId, LocalDate.now())).isTrue();
    }

    @Test
    void hasOccupancyOnDate_falseWhenMoveInIsAfterMenuDate() {
        MemberEntity member = MemberEntity.builder().build();
        member.setId(memberId);
        LocalDate menuDate = LocalDate.of(2026, 7, 4);
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(pgSpace)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(menuDate.plusDays(1))
                .build();
        when(occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId))
                .thenReturn(Optional.of(occupancy));

        assertThat(policy.hasOccupancyOnDate(pgSpace, memberId, menuDate)).isFalse();
    }

    @Test
    void hasOccupancyOnDate_falseWhenVacatedBeforeMenuDate() {
        MemberEntity member = MemberEntity.builder().build();
        member.setId(memberId);
        LocalDate menuDate = LocalDate.of(2026, 7, 4);
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(pgSpace)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(menuDate.minusDays(10))
                .vacatedAt(LocalDateTime.of(2026, 7, 3, 12, 0))
                .build();
        when(occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId))
                .thenReturn(Optional.of(occupancy));

        assertThat(policy.hasOccupancyOnDate(pgSpace, memberId, menuDate)).isFalse();
    }

    @Test
    void hasMemberOccupancyOnDate_preloadedEmptyDoesNotQueryRepository() {
        assertThat(policy.hasMemberOccupancyOnDate(pgSpace, Optional.empty(), memberId, LocalDate.now(), true))
                .isFalse();
    }

    @Test
    void hasMemberOccupancyOnDate_preloadedOccupancyUsesInMemoryOnly() {
        MemberEntity member = MemberEntity.builder().build();
        member.setId(memberId);
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(pgSpace)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.now().minusDays(1))
                .build();

        assertThat(policy.hasMemberOccupancyOnDate(
                        pgSpace, Optional.of(occupancy), memberId, LocalDate.now(), true))
                .isTrue();
    }

    @Test
    void occupiedMemberIdsForDate_emptyOptionalForMess() {
        SpaceEntity messSpace = SpaceEntity.builder().type(SpaceType.MESS).build();
        assertThat(policy.occupiedMemberIdsForDate(messSpace, LocalDate.now())).isEmpty();
    }

    @Test
    void occupiedMemberIdsForDate_returnsActiveMembersForPg() {
        MemberEntity member = MemberEntity.builder().build();
        member.setId(memberId);
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(pgSpace)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.now())
                .build();
        when(occupancyRepository.findActiveBySpaceId(spaceId)).thenReturn(List.of(occupancy));

        Optional<java.util.Set<UUID>> ids = policy.occupiedMemberIdsForDate(pgSpace, LocalDate.now());

        assertThat(ids).isPresent();
        assertThat(ids.get()).containsExactly(memberId);
    }
}
