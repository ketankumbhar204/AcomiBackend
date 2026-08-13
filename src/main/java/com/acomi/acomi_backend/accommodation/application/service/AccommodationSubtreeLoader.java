package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationDeletionRoot;
import com.acomi.acomi_backend.accommodation.domain.policy.AccommodationDeletionSubtree;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccommodationSubtreeLoader {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    public AccommodationDeletionSubtree loadBed(UUID spaceId, UUID bedId) {
        BedEntity bed = bedRepository.findByIdAndSpaceId(bedId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Bed", bedId));

        return AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.BED)
                .rootName(bed.getName())
                .bed(bed)
                .beds(List.of(bed))
                .build();
    }

    public AccommodationDeletionSubtree loadRoom(UUID spaceId, UUID roomId) {
        RoomEntity room = roomRepository.findByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        List<BedEntity> beds = bedRepository.findAllByRoomId(roomId);

        return AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.ROOM)
                .rootName(room.getName())
                .room(room)
                .rooms(List.of(room))
                .beds(beds)
                .build();
    }

    public AccommodationDeletionSubtree loadFloor(UUID spaceId, UUID floorId) {
        FloorEntity floor = floorRepository.findByIdAndSpaceId(floorId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Floor", "id", floorId));

        List<UnitEntity> units = unitRepository.findAllByFloorId(floorId);
        List<RoomEntity> rooms = roomRepository.findAllByFloorIdIncludingUnits(floorId);
        List<BedEntity> beds = bedRepository.findAllByFloorIdIncludingUnits(floorId);

        return AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.FLOOR)
                .rootName(floor.getName())
                .floor(floor)
                .floors(List.of(floor))
                .units(units)
                .rooms(rooms)
                .beds(beds)
                .build();
    }

    public AccommodationDeletionSubtree loadUnit(UUID spaceId, UUID unitId) {
        UnitEntity unit = unitRepository.findByIdAndSpaceId(unitId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));

        List<RoomEntity> rooms = roomRepository.findAllByUnitId(unitId);
        List<BedEntity> beds = bedRepository.findAllByUnitId(unitId);

        return AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.UNIT)
                .rootName(unit.getName())
                .unit(unit)
                .units(List.of(unit))
                .rooms(rooms)
                .beds(beds)
                .build();
    }

    public AccommodationDeletionSubtree loadBuilding(UUID spaceId, UUID buildingId) {
        BuildingEntity building = buildingRepository.findByIdAndSpaceId(buildingId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Building", "id", buildingId));

        List<FloorEntity> floors = floorRepository.findAllByBuildingId(buildingId);
        List<UnitEntity> units = unitRepository.findAllByBuildingId(buildingId);
        List<RoomEntity> rooms = collectRoomsForBuilding(buildingId, floors, units);
        List<BedEntity> beds = collectBedsForRooms(buildingId, rooms);

        return AccommodationDeletionSubtree.builder()
                .rootType(AccommodationDeletionRoot.BUILDING)
                .rootName(building.getName())
                .building(building)
                .floors(floors)
                .units(units)
                .rooms(rooms)
                .beds(beds)
                .build();
    }

    private List<RoomEntity> collectRoomsForBuilding(
            UUID buildingId, List<FloorEntity> floors, List<UnitEntity> units) {
        Map<UUID, RoomEntity> byId = new LinkedHashMap<>();
        for (RoomEntity room : roomRepository.findAllByBuildingId(buildingId)) {
            byId.put(room.getId(), room);
        }
        for (FloorEntity floor : floors) {
            for (RoomEntity room : roomRepository.findAllByFloorIdIncludingUnits(floor.getId())) {
                byId.putIfAbsent(room.getId(), room);
            }
        }
        for (UnitEntity unit : units) {
            for (RoomEntity room : roomRepository.findAllByUnitId(unit.getId())) {
                byId.putIfAbsent(room.getId(), room);
            }
        }
        return List.copyOf(byId.values());
    }

    private List<BedEntity> collectBedsForRooms(UUID buildingId, List<RoomEntity> rooms) {
        Map<UUID, BedEntity> byId = new LinkedHashMap<>();
        for (BedEntity bed : bedRepository.findAllByBuildingId(buildingId)) {
            byId.put(bed.getId(), bed);
        }
        for (RoomEntity room : rooms) {
            for (BedEntity bed : bedRepository.findAllByRoomId(room.getId())) {
                byId.putIfAbsent(bed.getId(), bed);
            }
        }
        return List.copyOf(byId.values());
    }
}
