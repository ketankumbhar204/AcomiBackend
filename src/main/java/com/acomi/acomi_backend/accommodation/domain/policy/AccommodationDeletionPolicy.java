package com.acomi.acomi_backend.accommodation.domain.policy;

import com.acomi.acomi_backend.accommodation.domain.model.AccommodationDeletionRoot;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.domain.port.AccommodationOccupancyPort;
import com.acomi.acomi_backend.accommodation.domain.port.AccommodationReferencePort;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccommodationDeletionPolicy {

    private final AccommodationOccupancyPort occupancyPort;
    private final AccommodationReferencePort referencePort;

    public DeletionEvaluation evaluate(AccommodationDeletionSubtree subtree) {
        for (BedEntity bed : subtree.getBeds()) {
            DeletionEvaluation evaluation = evaluateBedNode(bed);
            if (!evaluation.isDeletable()) {
                return wrapSubtreeBlock(subtree, evaluation);
            }
        }

        for (RoomEntity room : subtree.getRooms()) {
            DeletionEvaluation evaluation = evaluateRoomNode(room);
            if (!evaluation.isDeletable()) {
                return wrapSubtreeBlock(subtree, evaluation);
            }
        }

        for (FloorEntity floor : subtree.getFloors()) {
            DeletionEvaluation evaluation = evaluateFloorNode(floor);
            if (!evaluation.isDeletable()) {
                return wrapSubtreeBlock(subtree, evaluation);
            }
        }

        for (UnitEntity unit : subtree.getUnits()) {
            DeletionEvaluation evaluation = evaluateUnitNode(unit);
            if (!evaluation.isDeletable()) {
                return wrapSubtreeBlock(subtree, evaluation);
            }
        }

        if (subtree.getBuilding() != null) {
            DeletionEvaluation evaluation = evaluateBuildingNode(subtree.getBuilding());
            if (!evaluation.isDeletable()) {
                return wrapSubtreeBlock(subtree, evaluation);
            }
        }

        return DeletionEvaluation.allowed();
    }

    private DeletionEvaluation evaluateBedNode(BedEntity bed) {
        if (bed.getStatus() == AccommodationStatus.OCCUPIED || bed.getStatus() == AccommodationStatus.RESERVED) {
            return DeletionEvaluation.blocked("active occupancy exists");
        }
        return evaluateBedNode(bed.getId());
    }

    private DeletionEvaluation evaluateBedNode(UUID bedId) {
        if (occupancyPort.hasActiveOccupancyForBed(bedId)) {
            return DeletionEvaluation.blocked("active occupancy exists");
        }
        if (occupancyPort.hasOccupancyHistoryForBed(bedId)) {
            return DeletionEvaluation.blocked("occupancy history exists");
        }
        if (referencePort.isBedReferenced(bedId)) {
            return DeletionEvaluation.blocked("linked records exist");
        }
        return DeletionEvaluation.allowed();
    }

    private DeletionEvaluation evaluateRoomNode(RoomEntity room) {
        if (room.getStatus() == AccommodationStatus.OCCUPIED || room.getStatus() == AccommodationStatus.RESERVED) {
            return DeletionEvaluation.blocked("active occupancy exists");
        }
        if (occupancyPort.hasActiveOccupancyForRoom(room.getId())) {
            return DeletionEvaluation.blocked("active occupancy exists");
        }
        if (occupancyPort.hasOccupancyHistoryForRoom(room.getId())) {
            return DeletionEvaluation.blocked("occupancy history exists");
        }
        if (referencePort.isRoomReferenced(room.getId())) {
            return DeletionEvaluation.blocked("linked records exist");
        }
        return DeletionEvaluation.allowed();
    }

    private DeletionEvaluation evaluateFloorNode(FloorEntity floor) {
        if (occupancyPort.hasOccupancyHistoryForFloor(floor.getId())) {
            return DeletionEvaluation.blocked("occupancy history exists");
        }
        if (referencePort.isFloorReferenced(floor.getId())) {
            return DeletionEvaluation.blocked("linked records exist");
        }
        return DeletionEvaluation.allowed();
    }

    private DeletionEvaluation evaluateUnitNode(UnitEntity unit) {
        if (occupancyPort.hasOccupancyHistoryForUnit(unit.getId())) {
            return DeletionEvaluation.blocked("occupancy history exists");
        }
        if (referencePort.isUnitReferenced(unit.getId())) {
            return DeletionEvaluation.blocked("linked records exist");
        }
        return DeletionEvaluation.allowed();
    }

    private DeletionEvaluation evaluateBuildingNode(BuildingEntity building) {
        if (occupancyPort.hasOccupancyHistoryForBuilding(building.getId())) {
            return DeletionEvaluation.blocked("occupancy history exists");
        }
        if (referencePort.isBuildingReferenced(building.getId())) {
            return DeletionEvaluation.blocked("linked records exist");
        }
        return DeletionEvaluation.allowed();
    }

    private DeletionEvaluation wrapSubtreeBlock(
            AccommodationDeletionSubtree subtree, DeletionEvaluation nodeEvaluation) {
        if (subtree.getRootType() == AccommodationDeletionRoot.BED) {
            return DeletionEvaluation.blocked(toBedMessage(nodeEvaluation.getBlockReason()));
        }
        return DeletionEvaluation.blocked(String.format(
                "Cannot delete %s because %s within the accommodation structure. Deactivate instead.",
                subtree.rootLabel(),
                nodeEvaluation.getBlockReason()));
    }

    private String toBedMessage(String reason) {
        return switch (reason) {
            case "active occupancy exists" -> "Cannot delete bed because it is currently occupied.";
            case "occupancy history exists" ->
                    "Cannot delete bed because occupancy history exists. Deactivate instead.";
            case "linked records exist" ->
                    "Cannot delete bed because it is linked to other records. Deactivate instead.";
            default -> "Cannot delete bed. Deactivate instead.";
        };
    }
}
