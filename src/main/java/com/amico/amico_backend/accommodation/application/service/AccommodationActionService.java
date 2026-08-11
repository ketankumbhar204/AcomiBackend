package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.api.dto.response.AccommodationActionMetadata;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationDeletionPolicy;
import com.amico.amico_backend.accommodation.domain.policy.DeletionEvaluation;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.amico.amico_backend.member.domain.model.MembershipRole;
import com.amico.amico_backend.member.infrastructure.persistence.repository.SpaceMembershipRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccommodationActionService {

    private final AccommodationDeletionPolicy deletionPolicy;
    private final AccommodationSubtreeLoader subtreeLoader;
    private final SpaceMembershipRepository spaceMembershipRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;

    public AccommodationActionMetadata forBuilding(
            UUID spaceId, BuildingEntity building, UUID callerId) {
        boolean owner = isOwner(spaceId, callerId);
        boolean ownerOrManager = isOwnerOrManager(spaceId, callerId);
        boolean active = building.isActive();

        DeletionEvaluation deletion =
                deletionPolicy.evaluate(subtreeLoader.loadBuilding(spaceId, building.getId()));

        return AccommodationActionMetadata.builder()
                .canEdit(ownerOrManager && active)
                .canDeactivate(owner && active && canDeactivateBuilding(building.getId()))
                .canRestore(owner && !active)
                .canDelete(owner && active && deletion.isDeletable())
                .deleteReason(owner && active && !deletion.isDeletable() ? deletion.getBlockReason() : null)
                .build();
    }

    public AccommodationActionMetadata forFloor(UUID spaceId, FloorEntity floor, UUID callerId) {
        boolean owner = isOwner(spaceId, callerId);
        boolean ownerOrManager = isOwnerOrManager(spaceId, callerId);
        boolean active = floor.isActive();

        DeletionEvaluation deletion =
                deletionPolicy.evaluate(subtreeLoader.loadFloor(spaceId, floor.getId()));
        boolean parentActive = floor.getBuilding().isActive();

        return AccommodationActionMetadata.builder()
                .canEdit(ownerOrManager && active)
                .canDeactivate(owner && active && canDeactivateFloor(floor.getId()))
                .canRestore(owner && !active && parentActive)
                .canDelete(owner && active && deletion.isDeletable())
                .deleteReason(owner && active && !deletion.isDeletable() ? deletion.getBlockReason() : null)
                .build();
    }

    public AccommodationActionMetadata forUnit(UUID spaceId, UnitEntity unit, UUID callerId) {
        boolean owner = isOwner(spaceId, callerId);
        boolean ownerOrManager = isOwnerOrManager(spaceId, callerId);
        boolean active = unit.isActive();

        DeletionEvaluation deletion =
                deletionPolicy.evaluate(subtreeLoader.loadUnit(spaceId, unit.getId()));
        boolean parentActive = unit.getBuilding().isActive();

        return AccommodationActionMetadata.builder()
                .canEdit(ownerOrManager && active)
                .canDeactivate(owner && active && canDeactivateUnit(unit.getId()))
                .canRestore(owner && !active && parentActive)
                .canDelete(owner && active && deletion.isDeletable())
                .deleteReason(owner && active && !deletion.isDeletable() ? deletion.getBlockReason() : null)
                .build();
    }

    public AccommodationActionMetadata forRoom(UUID spaceId, RoomEntity room, UUID callerId) {
        boolean owner = isOwner(spaceId, callerId);
        boolean ownerOrManager = isOwnerOrManager(spaceId, callerId);
        boolean active = room.isActive();

        DeletionEvaluation deletion =
                deletionPolicy.evaluate(subtreeLoader.loadRoom(spaceId, room.getId()));
        boolean parentActive = room.getFloor() != null
                ? room.getFloor().isActive() && room.getFloor().getBuilding().isActive()
                : room.getUnit().isActive() && room.getUnit().getBuilding().isActive();

        return AccommodationActionMetadata.builder()
                .canEdit(ownerOrManager && active)
                .canDeactivate(owner && active && canDeactivateRoom(room.getId()))
                .canRestore(owner && !active && parentActive)
                .canDelete(owner && active && deletion.isDeletable())
                .deleteReason(owner && active && !deletion.isDeletable() ? deletion.getBlockReason() : null)
                .build();
    }

    public AccommodationActionMetadata forBed(UUID spaceId, BedEntity bed, UUID callerId) {
        boolean owner = isOwner(spaceId, callerId);
        boolean ownerOrManager = isOwnerOrManager(spaceId, callerId);
        boolean active = bed.isActive();

        DeletionEvaluation deletion =
                deletionPolicy.evaluate(subtreeLoader.loadBed(spaceId, bed.getId()));
        boolean parentActive = bed.getRoom().isActive();

        return AccommodationActionMetadata.builder()
                .canEdit(ownerOrManager && active)
                .canDeactivate(owner && active)
                .canRestore(owner && !active && parentActive)
                .canDelete(owner && active && deletion.isDeletable())
                .deleteReason(owner && active && !deletion.isDeletable() ? deletion.getBlockReason() : null)
                .build();
    }

    private boolean canDeactivateBuilding(UUID buildingId) {
        return !floorRepository.existsByBuildingIdAndIsActiveTrue(buildingId)
                && !unitRepository.existsByBuildingIdAndIsActiveTrue(buildingId);
    }

    private boolean canDeactivateFloor(UUID floorId) {
        return !roomRepository.existsByFloorIdAndIsActiveTrue(floorId);
    }

    private boolean canDeactivateUnit(UUID unitId) {
        return !roomRepository.existsByUnitIdAndIsActiveTrue(unitId);
    }

    private boolean canDeactivateRoom(UUID roomId) {
        return !bedRepository.existsByRoomIdAndIsActiveTrue(roomId);
    }

    private boolean isOwner(UUID spaceId, UUID callerId) {
        return spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, spaceId, List.of(MembershipRole.OWNER));
    }

    private boolean isOwnerOrManager(UUID spaceId, UUID callerId) {
        return spaceMembershipRepository.existsByUserIdAndSpaceIdAndRoleIn(
                callerId, spaceId, List.of(MembershipRole.OWNER, MembershipRole.MANAGER));
    }
}
