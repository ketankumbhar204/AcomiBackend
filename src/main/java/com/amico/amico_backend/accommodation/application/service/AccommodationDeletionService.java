package com.amico.amico_backend.accommodation.application.service;

import com.amico.amico_backend.accommodation.domain.model.AccommodationDeletionRoot;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationDeletionPolicy;
import com.amico.amico_backend.accommodation.domain.policy.AccommodationDeletionSubtree;
import com.amico.amico_backend.accommodation.domain.policy.DeletionEvaluation;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.AccommodationSetupIdempotencyRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.BuildingRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.FloorRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.amico.amico_backend.accommodation.infrastructure.persistence.repository.UnitRepository;
import com.amico.amico_backend.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccommodationDeletionService {

    private final AccommodationDeletionPolicy deletionPolicy;
    private final AccommodationSubtreeLoader subtreeLoader;
    private final AccommodationAccessService accessService;
    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final FloorRepository floorRepository;
    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final AccommodationSetupIdempotencyRepository setupIdempotencyRepository;

    @Transactional
    public void deleteBed(UUID spaceId, UUID bedId, UUID callerId) {
        log.info("Cascade deleting bed: spaceId={}, bedId={}, callerId={}", spaceId, bedId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        AccommodationDeletionSubtree subtree = subtreeLoader.loadBed(spaceId, bedId);
        assertDeletable(deletionPolicy.evaluate(subtree));
        cascadeDelete(subtree);
    }

    @Transactional
    public void deleteRoom(UUID spaceId, UUID roomId, UUID callerId) {
        log.info("Cascade deleting room: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        AccommodationDeletionSubtree subtree = subtreeLoader.loadRoom(spaceId, roomId);
        assertDeletable(deletionPolicy.evaluate(subtree));
        cascadeDelete(subtree);
    }

    @Transactional
    public void deleteFloor(UUID spaceId, UUID floorId, UUID callerId) {
        log.info("Cascade deleting floor: spaceId={}, floorId={}, callerId={}", spaceId, floorId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        AccommodationDeletionSubtree subtree = subtreeLoader.loadFloor(spaceId, floorId);
        assertDeletable(deletionPolicy.evaluate(subtree));
        cascadeDelete(subtree);
    }

    @Transactional
    public void deleteUnit(UUID spaceId, UUID unitId, UUID callerId) {
        log.info("Cascade deleting unit: spaceId={}, unitId={}, callerId={}", spaceId, unitId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        AccommodationDeletionSubtree subtree = subtreeLoader.loadUnit(spaceId, unitId);
        assertDeletable(deletionPolicy.evaluate(subtree));
        cascadeDelete(subtree);
    }

    @Transactional
    public void deleteBuilding(UUID spaceId, UUID buildingId, UUID callerId) {
        log.info("Cascade deleting building: spaceId={}, buildingId={}, callerId={}",
                spaceId, buildingId, callerId);
        accessService.assertCanDeactivateStructure(spaceId, callerId);

        AccommodationDeletionSubtree subtree = subtreeLoader.loadBuilding(spaceId, buildingId);
        assertDeletable(deletionPolicy.evaluate(subtree));
        cascadeDelete(subtree);
    }

    private void cascadeDelete(AccommodationDeletionSubtree subtree) {
        if (!subtree.getBeds().isEmpty()) {
            bedRepository.deleteAll(subtree.getBeds());
        }
        if (!subtree.getRooms().isEmpty()) {
            roomRepository.deleteAll(subtree.getRooms());
        }

        switch (subtree.getRootType()) {
            case BUILDING -> {
                if (!subtree.getUnits().isEmpty()) {
                    unitRepository.deleteAll(subtree.getUnits());
                }
                if (!subtree.getFloors().isEmpty()) {
                    floorRepository.deleteAll(subtree.getFloors());
                }
                setupIdempotencyRepository.deleteByBuildingId(subtree.getBuilding().getId());
                buildingRepository.delete(subtree.getBuilding());
            }
            case FLOOR -> {
                if (!subtree.getUnits().isEmpty()) {
                    unitRepository.deleteAll(subtree.getUnits());
                }
                floorRepository.delete(subtree.getFloor());
            }
            case UNIT -> unitRepository.delete(subtree.getUnit());
            case ROOM -> { /* room deleted above */ }
            case BED -> { /* bed deleted above */ }
        }
    }

    private void assertDeletable(DeletionEvaluation evaluation) {
        if (!evaluation.isDeletable()) {
            throw new BusinessException(evaluation.getBlockReason());
        }
    }
}
