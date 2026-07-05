package com.countin.countin_backend.accommodation.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.countin.countin_backend.accommodation.domain.model.AccommodationDeletionRoot;
import com.countin.countin_backend.accommodation.domain.model.AccommodationStatus;
import com.countin.countin_backend.accommodation.domain.policy.AccommodationDeletionPolicy;
import com.countin.countin_backend.accommodation.domain.policy.AccommodationDeletionSubtree;
import com.countin.countin_backend.accommodation.domain.policy.DeletionEvaluation;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.countin.countin_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.AccommodationSetupIdempotencyRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.countin.countin_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.countin.countin_backend.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccommodationDeletionServiceTest {

    @Mock
    private AccommodationDeletionPolicy deletionPolicy;

    @Mock
    private AccommodationSubtreeLoader subtreeLoader;

    @Mock
    private AccommodationAccessService accessService;

    @Mock
    private BedRepository bedRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private AccommodationSetupIdempotencyRepository setupIdempotencyRepository;

    @InjectMocks
    private AccommodationDeletionService deletionService;

    private final UUID spaceId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID roomId = UUID.randomUUID();
    private final UUID floorId = UUID.randomUUID();

    @Test
    void deleteBuilding_whenUnitRoomsExist_cascadeDeletesRoomsBeforeUnits() {
        UUID buildingId = UUID.randomUUID();
        SpaceEntity space = SpaceEntity.builder().name("PG").build();
        space.setId(spaceId);

        BuildingEntity building = BuildingEntity.builder().space(space).name("Block A").code("A").build();
        building.setId(buildingId);

        FloorEntity floor = FloorEntity.builder().building(building).name("Floor 1").floorNumber(1).build();
        floor.setId(UUID.randomUUID());

        UnitEntity unit = UnitEntity.builder()
                .building(building)
                .floor(floor)
                .name("Apt 101")
                .unitNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        unit.setId(UUID.randomUUID());

        RoomEntity room = RoomEntity.builder()
                .unit(unit)
                .name("Bedroom")
                .roomNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        room.setId(UUID.randomUUID());

        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.BUILDING)
                .rootName("Block A")
                .building(building)
                .floors(List.of(floor))
                .units(List.of(unit))
                .rooms(List.of(room))
                .beds(List.of())
                .build();

        when(subtreeLoader.loadBuilding(spaceId, buildingId)).thenReturn(subtree);
        when(deletionPolicy.evaluate(subtree)).thenReturn(DeletionEvaluation.allowed());
        when(setupIdempotencyRepository.deleteByBuildingId(buildingId)).thenReturn(1);

        deletionService.deleteBuilding(spaceId, buildingId, callerId);

        verify(setupIdempotencyRepository).deleteByBuildingId(buildingId);
        verify(roomRepository).deleteAll(subtree.getRooms());
        verify(unitRepository).deleteAll(subtree.getUnits());
        verify(floorRepository).deleteAll(subtree.getFloors());
        verify(buildingRepository).delete(building);
    }

    @Test
    void deleteFloor_whenApartmentsExist_cascadeDeletesUnitsBeforeFloor() {
        SpaceEntity space = SpaceEntity.builder().name("PG").build();
        space.setId(spaceId);

        BuildingEntity building = BuildingEntity.builder().space(space).name("Building C").code("BLD-C").build();
        building.setId(UUID.randomUUID());

        FloorEntity floor = FloorEntity.builder().building(building).name("Floor 1").floorNumber(1).build();
        floor.setId(floorId);

        UnitEntity unit = UnitEntity.builder()
                .building(building)
                .floor(floor)
                .name("Apt 101")
                .unitNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();

        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.FLOOR)
                .rootName("Floor 1")
                .floor(floor)
                .floors(List.of(floor))
                .units(List.of(unit))
                .rooms(List.of())
                .beds(List.of())
                .build();

        when(subtreeLoader.loadFloor(spaceId, floorId)).thenReturn(subtree);
        when(deletionPolicy.evaluate(subtree)).thenReturn(DeletionEvaluation.allowed());

        deletionService.deleteFloor(spaceId, floorId, callerId);

        verify(unitRepository).deleteAll(subtree.getUnits());
        verify(floorRepository).delete(floor);
    }

    @Test
    void deleteRoom_whenSubtreeClean_cascadeDeletesBedsAndRoom() {
        RoomEntity room = RoomEntity.builder()
                .name("Room 101")
                .roomNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        room.setId(roomId);

        BedEntity bed = BedEntity.builder()
                .name("Bed A")
                .bedNumber("A")
                .status(AccommodationStatus.AVAILABLE)
                .build();

        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.ROOM)
                .rootName("Room 101")
                .room(room)
                .rooms(List.of(room))
                .beds(List.of(bed))
                .build();

        when(subtreeLoader.loadRoom(spaceId, roomId)).thenReturn(subtree);
        when(deletionPolicy.evaluate(subtree)).thenReturn(DeletionEvaluation.allowed());

        deletionService.deleteRoom(spaceId, roomId, callerId);

        verify(bedRepository).deleteAll(subtree.getBeds());
        verify(roomRepository).deleteAll(subtree.getRooms());
        verify(accessService).assertCanDeactivateStructure(spaceId, callerId);
    }

    @Test
    void deleteRoom_whenSubtreeBlocked_doesNotDelete() {
        RoomEntity room = RoomEntity.builder()
                .name("Room 101")
                .roomNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        room.setId(roomId);

        AccommodationDeletionSubtree subtree = AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.ROOM)
                .rootName("Room 101")
                .room(room)
                .rooms(List.of(room))
                .beds(List.of())
                .build();

        when(subtreeLoader.loadRoom(spaceId, roomId)).thenReturn(subtree);
        when(deletionPolicy.evaluate(subtree))
                .thenReturn(DeletionEvaluation.blocked(
                        "Cannot delete Room 101 because occupancy history exists within the accommodation structure. Deactivate instead."));

        assertThatThrownBy(() -> deletionService.deleteRoom(spaceId, roomId, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("occupancy history");

        verify(bedRepository, never()).deleteAll(any());
        verify(roomRepository, never()).deleteAll(any());
    }
}
