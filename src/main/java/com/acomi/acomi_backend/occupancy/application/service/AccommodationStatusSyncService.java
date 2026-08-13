package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccommodationStatusSyncService {

    private final OccupancyRepository occupancyRepository;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final UnitRepository unitRepository;

    @Transactional
    public void markReserved(OccupancyTargetService.ResolvedTarget target) {
        syncTarget(target.getTargetType(), idOf(target), parentRoomId(target), parentUnitId(target));
    }

    @Transactional
    public void markOccupied(OccupancyTargetService.ResolvedTarget target) {
        syncTarget(target.getTargetType(), idOf(target), parentRoomId(target), parentUnitId(target));
    }

    @Transactional
    public void releaseTarget(AllocationTargetType targetType, UUID bedId, UUID roomId, UUID unitId) {
        syncTarget(targetType, primaryId(targetType, bedId, roomId, unitId), roomId, unitId);
    }

    @Transactional
    public void syncTarget(AllocationTargetType targetType, UUID bedId, UUID roomId, UUID unitId) {
        switch (targetType) {
            case BED -> {
                if (bedId != null) {
                    applyBedStatus(bedId);
                }
                if (roomId != null) {
                    syncRoomStatus(roomId);
                }
            }
            case ROOM -> {
                if (roomId != null) {
                    applyRoomStatus(roomId);
                }
                if (unitId != null) {
                    syncUnitStatus(unitId);
                }
            }
            case UNIT -> {
                if (unitId != null) {
                    applyUnitStatus(unitId);
                }
            }
        }
    }

    @Transactional
    public void setMaintenance(UUID spaceId, AllocationTargetType targetType, UUID targetId) {
        setOperatorStatus(spaceId, targetType, targetId, AccommodationStatus.MAINTENANCE);
    }

    @Transactional
    public void setBlocked(UUID spaceId, AllocationTargetType targetType, UUID targetId) {
        setOperatorStatus(spaceId, targetType, targetId, AccommodationStatus.BLOCKED);
    }

    private void setOperatorStatus(
            UUID spaceId, AllocationTargetType targetType, UUID targetId, AccommodationStatus status) {
        assertNoBlockingOccupancy(targetType, targetId);
        switch (targetType) {
            case BED -> {
                BedEntity bed = bedRepository.findByIdAndSpaceId(targetId, spaceId)
                        .orElseThrow(() -> ResourceNotFoundException.notInSpace("Bed", targetId));
                bed.setStatus(status);
                bedRepository.save(bed);
                syncRoomStatus(bed.getRoom().getId());
            }
            case ROOM -> {
                RoomEntity room = roomRepository.findByIdAndSpaceId(targetId, spaceId)
                        .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", targetId));
                room.setStatus(status);
                roomRepository.save(room);
                if (room.getUnit() != null) {
                    syncUnitStatus(room.getUnit().getId());
                }
            }
            case UNIT -> {
                UnitEntity unit = unitRepository.findByIdAndSpaceId(targetId, spaceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", targetId));
                unit.setStatus(status);
                unitRepository.save(unit);
            }
        }
    }

    private void assertNoBlockingOccupancy(AllocationTargetType targetType, UUID targetId) {
        boolean blocked = switch (targetType) {
            case BED -> hasBlockingBedOccupancy(targetId);
            case ROOM -> hasBlockingRoomOccupancy(targetId);
            case UNIT -> hasBlockingUnitOccupancy(targetId);
        };
        if (blocked) {
            throw new BusinessException("Cannot set status while an active or reserved occupancy exists");
        }
    }

    private void applyBedStatus(UUID bedId) {
        bedRepository.findById(bedId).ifPresent(bed -> {
            if (isOperatorLocked(bed.getStatus())) {
                return;
            }
            bed.setStatus(deriveBedStatus(bedId));
            bedRepository.save(bed);
        });
    }

    private void applyRoomStatus(UUID roomId) {
        roomRepository.findById(roomId).ifPresent(room -> {
            if (isOperatorLocked(room.getStatus())) {
                return;
            }
            room.setStatus(deriveRoomStatus(roomId));
            roomRepository.save(room);
        });
    }

    private void applyUnitStatus(UUID unitId) {
        unitRepository.findById(unitId).ifPresent(unit -> {
            if (isOperatorLocked(unit.getStatus())) {
                return;
            }
            unit.setStatus(deriveUnitStatus(unitId));
            unitRepository.save(unit);
        });
    }

    private void syncRoomStatus(UUID roomId) {
        applyRoomStatus(roomId);
        roomRepository.findById(roomId).ifPresent(room -> {
            if (room.getUnit() != null) {
                syncUnitStatus(room.getUnit().getId());
            }
        });
    }

    private void syncUnitStatus(UUID unitId) {
        applyUnitStatus(unitId);
    }

    private AccommodationStatus deriveBedStatus(UUID bedId) {
        if (occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)) {
            return AccommodationStatus.OCCUPIED;
        }
        if (occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)) {
            return AccommodationStatus.RESERVED;
        }
        return AccommodationStatus.AVAILABLE;
    }

    private AccommodationStatus deriveRoomStatus(UUID roomId) {
        if (occupancyRepository.existsByRoomIdAndStatus(roomId, OccupancyStatus.ACTIVE)
                || bedRepository.findActiveByRoomId(roomId).stream()
                        .anyMatch(bed -> occupancyRepository.existsByBedIdAndStatus(
                                bed.getId(), OccupancyStatus.ACTIVE))) {
            return AccommodationStatus.OCCUPIED;
        }
        if (occupancyRepository.existsByRoomIdAndStatus(roomId, OccupancyStatus.RESERVED)
                || bedRepository.findActiveByRoomId(roomId).stream()
                        .anyMatch(bed -> occupancyRepository.existsByBedIdAndStatus(
                                bed.getId(), OccupancyStatus.RESERVED))) {
            return AccommodationStatus.RESERVED;
        }
        return AccommodationStatus.AVAILABLE;
    }

    private AccommodationStatus deriveUnitStatus(UUID unitId) {
        if (occupancyRepository.existsByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE)
                || roomRepository.findActiveByUnitId(unitId).stream()
                        .anyMatch(room -> occupancyRepository.existsByRoomIdAndStatus(
                                        room.getId(), OccupancyStatus.ACTIVE)
                                || bedRepository.findActiveByRoomId(room.getId()).stream()
                                        .anyMatch(bed -> occupancyRepository.existsByBedIdAndStatus(
                                                bed.getId(), OccupancyStatus.ACTIVE)))) {
            return AccommodationStatus.OCCUPIED;
        }
        if (occupancyRepository.existsByUnitIdAndStatus(unitId, OccupancyStatus.RESERVED)
                || roomRepository.findActiveByUnitId(unitId).stream()
                        .anyMatch(room -> occupancyRepository.existsByRoomIdAndStatus(
                                        room.getId(), OccupancyStatus.RESERVED)
                                || bedRepository.findActiveByRoomId(room.getId()).stream()
                                        .anyMatch(bed -> occupancyRepository.existsByBedIdAndStatus(
                                                bed.getId(), OccupancyStatus.RESERVED)))) {
            return AccommodationStatus.RESERVED;
        }
        return AccommodationStatus.AVAILABLE;
    }

    private boolean hasBlockingBedOccupancy(UUID bedId) {
        return occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED);
    }

    private boolean hasBlockingRoomOccupancy(UUID roomId) {
        return occupancyRepository.existsByRoomIdAndStatus(roomId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsByRoomIdAndStatus(roomId, OccupancyStatus.RESERVED);
    }

    private boolean hasBlockingUnitOccupancy(UUID unitId) {
        return occupancyRepository.existsByUnitIdAndStatus(unitId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsByUnitIdAndStatus(unitId, OccupancyStatus.RESERVED);
    }

    private boolean isOperatorLocked(AccommodationStatus status) {
        return status == AccommodationStatus.MAINTENANCE || status == AccommodationStatus.BLOCKED;
    }

    private UUID idOf(OccupancyTargetService.ResolvedTarget target) {
        return primaryId(
                target.getTargetType(),
                target.getBed() != null ? target.getBed().getId() : null,
                target.getRoom() != null ? target.getRoom().getId() : null,
                target.getUnit() != null ? target.getUnit().getId() : null);
    }

    private UUID parentRoomId(OccupancyTargetService.ResolvedTarget target) {
        return target.getRoom() != null ? target.getRoom().getId() : null;
    }

    private UUID parentUnitId(OccupancyTargetService.ResolvedTarget target) {
        return target.getUnit() != null ? target.getUnit().getId() : null;
    }

    private UUID primaryId(AllocationTargetType targetType, UUID bedId, UUID roomId, UUID unitId) {
        return switch (targetType) {
            case BED -> bedId;
            case ROOM -> roomId;
            case UNIT -> unitId;
        };
    }
}
