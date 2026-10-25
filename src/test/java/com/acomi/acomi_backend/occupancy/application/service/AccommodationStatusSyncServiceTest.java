package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.model.RoomType;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccommodationStatusSyncServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UnitRepository unitRepository;

    private AccommodationStatusSyncService service;

    @BeforeEach
    void setUp() {
        service = new AccommodationStatusSyncService(
                occupancyRepository, bedRepository, roomRepository, unitRepository);
    }

    @Test
    void roomStaysAvailableWhenAnyBedIsFree() {
        UUID roomId = UUID.randomUUID();
        RoomEntity room = room(roomId, AccommodationStatus.OCCUPIED);
        BedEntity occupied = bed(AccommodationStatus.OCCUPIED);
        BedEntity free = bed(AccommodationStatus.AVAILABLE);
        BedEntity otherFree = bed(AccommodationStatus.AVAILABLE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(bedRepository.findActiveByRoomId(roomId)).thenReturn(List.of(occupied, free, otherFree));
        when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                roomId, AllocationTargetType.ROOM, OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                roomId, AllocationTargetType.ROOM, OccupancyStatus.RESERVED)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(occupied.getId(), OccupancyStatus.ACTIVE)).thenReturn(true);
        when(occupancyRepository.existsByBedIdAndStatus(free.getId(), OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(free.getId(), OccupancyStatus.RESERVED)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(otherFree.getId(), OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(otherFree.getId(), OccupancyStatus.RESERVED)).thenReturn(false);

        service.syncTarget(AllocationTargetType.BED, null, roomId, null);

        assertThat(room.getStatus()).isEqualTo(AccommodationStatus.AVAILABLE);
        verify(roomRepository).save(room);
    }

    @Test
    void roomIsOccupiedOnlyWhenEveryBedIsOccupied() {
        UUID roomId = UUID.randomUUID();
        RoomEntity room = room(roomId, AccommodationStatus.AVAILABLE);
        BedEntity first = bed(AccommodationStatus.OCCUPIED);
        BedEntity second = bed(AccommodationStatus.OCCUPIED);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(bedRepository.findActiveByRoomId(roomId)).thenReturn(List.of(first, second));
        when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(any(), any(), any())).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(first.getId(), OccupancyStatus.ACTIVE)).thenReturn(true);
        when(occupancyRepository.existsByBedIdAndStatus(second.getId(), OccupancyStatus.ACTIVE)).thenReturn(true);

        service.syncTarget(AllocationTargetType.BED, null, roomId, null);

        assertThat(room.getStatus()).isEqualTo(AccommodationStatus.OCCUPIED);
        verify(roomRepository).save(room);
    }

    @Test
    void maintenanceRoomIsLeftUntouched() {
        UUID roomId = UUID.randomUUID();
        RoomEntity room = room(roomId, AccommodationStatus.MAINTENANCE);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        service.syncTarget(AllocationTargetType.BED, null, roomId, null);

        assertThat(room.getStatus()).isEqualTo(AccommodationStatus.MAINTENANCE);
        verify(roomRepository, never()).save(any());
    }

    private static RoomEntity room(UUID id, AccommodationStatus status) {
        RoomEntity room = RoomEntity.builder()
                .name("Room B")
                .roomNumber("B")
                .roomType(RoomType.SHARED)
                .status(status)
                .build();
        room.setId(id);
        return room;
    }

    private static BedEntity bed(AccommodationStatus status) {
        BedEntity bed = BedEntity.builder()
                .name("B")
                .bedNumber("B")
                .status(status)
                .build();
        bed.setId(UUID.randomUUID());
        return bed;
    }
}
