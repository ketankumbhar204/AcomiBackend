package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.application.support.StayAvailabilityRules;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StayRangeGuardTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_8 = LocalDate.of(2026, 11, 8);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);

    @Mock
    private OccupancyRepository occupancyRepository;

    @InjectMocks
    private StayRangeGuard guard;

    @Test
    void vacatedOverlapIsRejectedAndTheVacateDateIsFree() {
        UUID bedId = UUID.randomUUID();
        when(occupancyRepository.findByBed_IdAndStatusIn(eq(bedId), any()))
                .thenReturn(List.of(vacated(NOV_1, NOV_8)));

        assertThatThrownBy(() -> guard.assertAvailable(
                        AllocationTargetType.BED, bedId, null, null, NOV_1.plusDays(6), NOV_10, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);

        guard.assertAvailable(AllocationTargetType.BED, bedId, null, null, NOV_8, NOV_10, null);
    }

    @Test
    void openEndedStayBlocksALaterReservation() {
        UUID bedId = UUID.randomUUID();
        when(occupancyRepository.findByBed_IdAndStatusIn(eq(bedId), any()))
                .thenReturn(List.of(openEnded(NOV_1)));

        assertThatThrownBy(() -> guard.assertAvailable(
                        AllocationTargetType.BED, bedId, null, null, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 8), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);
    }

    @Test
    void roomStayConflictsWithABedInThatRoom() {
        UUID bedId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(occupancyRepository.findByBed_IdAndStatusIn(eq(bedId), any())).thenReturn(List.of());
        when(occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(eq(roomId), eq(AllocationTargetType.ROOM), any()))
                .thenReturn(List.of(roomStay(NOV_1, NOV_10)));

        assertThatThrownBy(() -> guard.assertAvailable(
                        AllocationTargetType.BED, bedId, roomId, null, NOV_8, NOV_10, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);
    }

    @Test
    void bedStayConflictsWithARoomStayOnThatRoom() {
        UUID roomId = UUID.randomUUID();
        when(occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(eq(roomId), eq(AllocationTargetType.ROOM), any()))
                .thenReturn(List.of());
        when(occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(eq(roomId), eq(AllocationTargetType.BED), any()))
                .thenReturn(List.of(bedStay(NOV_1, NOV_8)));

        assertThatThrownBy(() -> guard.assertAvailable(
                        AllocationTargetType.ROOM, null, roomId, null, NOV_1.plusDays(2), NOV_10, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);
    }

    @Test
    void siblingBedsAreNotCompared() {
        UUID bedId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        when(occupancyRepository.findByBed_IdAndStatusIn(eq(bedId), any())).thenReturn(List.of());
        when(occupancyRepository.findByRoom_IdAndTargetTypeAndStatusIn(eq(roomId), eq(AllocationTargetType.ROOM), any()))
                .thenReturn(List.of());

        guard.assertAvailable(AllocationTargetType.BED, bedId, roomId, null, NOV_1, NOV_8, null);

        verify(occupancyRepository, never())
                .findByRoom_IdAndTargetTypeAndStatusIn(any(), eq(AllocationTargetType.BED), any());
    }

    @Test
    void theSameOccupancyDoesNotConflictWithItself() {
        UUID bedId = UUID.randomUUID();
        OccupancyEntity self = vacated(NOV_1, NOV_8);
        when(occupancyRepository.findByBed_IdAndStatusIn(eq(bedId), any())).thenReturn(List.of(self));

        guard.assertAvailable(AllocationTargetType.BED, bedId, null, null, NOV_1, NOV_8, self.getId());
    }

    @Test
    void unitOverlapIsRejected() {
        UUID unitId = UUID.randomUUID();
        OccupancyEntity other = vacated(NOV_1, NOV_8);
        other.setTargetType(AllocationTargetType.UNIT);
        when(occupancyRepository.findByUnit_IdAndStatusIn(eq(unitId), any())).thenReturn(List.of(other));

        assertThatThrownBy(() ->
                        guard.assertAvailable(AllocationTargetType.UNIT, null, null, unitId, NOV_1, NOV_8, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);
    }

    private static OccupancyEntity vacated(LocalDate start, LocalDate vacatedOn) {
        OccupancyEntity row = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .targetType(AllocationTargetType.BED)
                .moveInDate(start)
                .build();
        row.setId(UUID.randomUUID());
        row.setVacatedAt(vacatedOn.atStartOfDay());
        return row;
    }

    private static OccupancyEntity openEnded(LocalDate start) {
        OccupancyEntity row = OccupancyEntity.builder()
                .status(OccupancyStatus.ACTIVE)
                .targetType(AllocationTargetType.BED)
                .moveInDate(start)
                .build();
        row.setId(UUID.randomUUID());
        return row;
    }

    private static OccupancyEntity roomStay(LocalDate start, LocalDate end) {
        OccupancyEntity row = OccupancyEntity.builder()
                .status(OccupancyStatus.VACATED)
                .targetType(AllocationTargetType.ROOM)
                .moveInDate(start)
                .build();
        row.setId(UUID.randomUUID());
        row.setVacatedAt(end.atStartOfDay());
        return row;
    }

    private static OccupancyEntity bedStay(LocalDate start, LocalDate end) {
        OccupancyEntity row = vacated(start, end);
        row.setTargetType(AllocationTargetType.BED);
        return row;
    }
}
