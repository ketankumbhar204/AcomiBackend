package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyTargetServiceTest {

    @Mock
    private BedRepository bedRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UnitRepository unitRepository;

    private OccupancyTargetService service;
    private UUID spaceId;
    private UUID roomId;
    private RoomEntity room;

    @BeforeEach
    void setUp() {
        service = new OccupancyTargetService(bedRepository, roomRepository, unitRepository);
        spaceId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        SpaceEntity space = SpaceEntity.builder().type(SpaceType.CO_LIVING).isActive(true).build();
        space.setId(spaceId);
        BuildingEntity building = BuildingEntity.builder().space(space).name("Block A").code("A").build();
        building.setId(UUID.randomUUID());
        FloorEntity floor = FloorEntity.builder().building(building).name("Floor 1").floorNumber(1).build();
        floor.setId(UUID.randomUUID());
        room = RoomEntity.builder()
                .floor(floor)
                .name("Room 101")
                .roomNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        room.setId(roomId);
    }

    @Test
    void resolveRoom_doesNotRejectBecauseChildBedsAreMaintenanceOrBlocked() {
        when(roomRepository.findByIdAndSpaceId(roomId, spaceId)).thenReturn(Optional.of(room));

        OccupancyTargetService.ResolvedTarget resolved = service.resolve(
                spaceId, SpaceType.CO_LIVING, AllocationTargetType.ROOM, null, roomId, null);

        assertThat(resolved.getTargetType()).isEqualTo(AllocationTargetType.ROOM);
        assertThat(resolved.getRoom()).isSameAs(room);
        verifyNoInteractions(bedRepository);
    }

    @Test
    void resolve_pgAndHostelRejectRoomTarget() {
        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.PG, AllocationTargetType.ROOM, null, roomId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only BED allocation");
        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.HOSTEL, AllocationTargetType.ROOM, null, roomId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only BED allocation");
        verify(roomRepository, never()).findByIdAndSpaceId(roomId, spaceId);
    }

    @Test
    void resolve_rentalAndCorporateStayRejectBedAndRoom() {
        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.RENTAL, AllocationTargetType.BED, UUID.randomUUID(), null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only UNIT allocation");
        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.CORPORATE_STAY, AllocationTargetType.ROOM, null, roomId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only UNIT allocation");
    }

    @Test
    void resolveBed_stillRejectsMaintenanceOrBlockedBed() {
        UUID bedId = UUID.randomUUID();
        BedEntity bed = BedEntity.builder().room(room).name("Bed A").bedNumber("A").status(AccommodationStatus.MAINTENANCE).build();
        bed.setId(bedId);
        when(bedRepository.findByIdAndSpaceId(bedId, spaceId)).thenReturn(Optional.of(bed));

        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.CO_LIVING, AllocationTargetType.BED, bedId, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MAINTENANCE");

        bed.setStatus(AccommodationStatus.BLOCKED);
        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.CO_LIVING, AllocationTargetType.BED, bedId, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BLOCKED");
    }

    @Test
    void resolveBed_rejectsOccupiedBedAsUnavailableBeforeOverlapChecks() {
        UUID bedId = UUID.randomUUID();
        BedEntity bed = BedEntity.builder()
                .room(room)
                .name("Bed A")
                .bedNumber("A")
                .status(AccommodationStatus.OCCUPIED)
                .build();
        bed.setId(bedId);
        when(bedRepository.findByIdAndSpaceId(bedId, spaceId)).thenReturn(Optional.of(bed));

        assertThatThrownBy(() -> service.resolve(
                        spaceId, SpaceType.CO_LIVING, AllocationTargetType.BED, bedId, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bed is not available for reservation");
    }
}
