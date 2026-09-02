package com.acomi.acomi_backend.accommodation.application.service;

import com.acomi.acomi_backend.accommodation.api.dto.request.CreateBedRequest;
import com.acomi.acomi_backend.accommodation.api.dto.request.UpdateBedRequest;
import com.acomi.acomi_backend.accommodation.api.dto.response.BedResponse;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.BedRepository;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.repository.RoomRepository;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyService;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BedService {

    private final BedRepository bedRepository;
    private final RoomRepository roomRepository;
    private final AccommodationAccessService accessService;
    private final AccommodationProfileService profileService;
    private final AccommodationActionService actionService;
    private final OccupancyService occupancyService;
    private final OccupancyRepository occupancyRepository;
    private final BedPricingPropagationService pricingPropagationService;

    @Transactional
    public BedResponse createBed(
            UUID spaceId, UUID roomId, UUID callerId, CreateBedRequest request) {
        log.info("Creating bed: spaceId={}, roomId={}, callerId={}, bedNumber={}",
                spaceId, roomId, callerId, request.getBedNumber());

        profileService.assertBedsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        RoomEntity room = roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        if (bedRepository.existsByRoomIdAndBedNumberAndIsActiveTrue(roomId, request.getBedNumber())) {
            throw new BusinessException("An active bed with this bed number already exists in the room");
        }

        AccommodationStatus status = request.getStatus() != null
                ? request.getStatus()
                : AccommodationStatus.AVAILABLE;

        BedEntity bed = BedEntity.builder()
                .room(room)
                .name(request.getName())
                .bedNumber(request.getBedNumber())
                .status(status)
                .defaultRent(request.getDefaultRent())
                .defaultDeposit(request.getDefaultDeposit())
                .build();

        bed = bedRepository.save(bed);
        pricingPropagationService.propagateFrom(spaceId, bed);
        return BedResponse.from(bed);
    }

    @Transactional(readOnly = true)
    public List<BedResponse> getBeds(UUID spaceId, UUID roomId, UUID callerId) {
        log.info("Listing beds: spaceId={}, roomId={}, callerId={}", spaceId, roomId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        return bedRepository.findActiveByRoomId(roomId)
                .stream()
                .map(BedResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public BedResponse getBed(UUID spaceId, UUID roomId, UUID bedId, UUID callerId) {
        log.info("Fetching bed: spaceId={}, roomId={}, bedId={}, callerId={}",
                spaceId, roomId, bedId, callerId);

        accessService.assertCanViewStructure(spaceId, callerId);
        roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        BedEntity bed = bedRepository.findActiveByIdAndRoomId(bedId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", bedId));

        return BedResponse.from(
                bed,
                actionService.forBed(spaceId, bed, callerId),
                occupancyService.findBedOccupant(bedId).orElse(null));
    }

    @Transactional(readOnly = true)
    public BedResponse getBedById(UUID spaceId, UUID bedId, UUID callerId) {
        accessService.assertCanViewStructure(spaceId, callerId);

        BedEntity bed = bedRepository.findByIdAndSpaceId(bedId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Bed", bedId));

        return BedResponse.from(
                bed,
                actionService.forBed(spaceId, bed, callerId),
                occupancyService.findBedOccupant(bedId).orElse(null));
    }

    @Transactional
    public BedResponse updateBed(
            UUID spaceId, UUID roomId, UUID bedId, UUID callerId, UpdateBedRequest request) {
        log.info("Updating bed: spaceId={}, roomId={}, bedId={}, callerId={}",
                spaceId, roomId, bedId, callerId);

        profileService.assertBedsAllowed(spaceId);
        accessService.assertCanManageStructure(spaceId, callerId);

        roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        BedEntity bed = bedRepository.findActiveByIdAndRoomId(bedId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", bedId));

        if (!bed.getBedNumber().equals(request.getBedNumber())
                && bedRepository.existsByRoomIdAndBedNumberAndIsActiveTrue(roomId, request.getBedNumber())) {
            throw new BusinessException("An active bed with this bed number already exists in the room");
        }

        bed.setName(request.getName());
        bed.setBedNumber(request.getBedNumber());
        if (request.getStatus() == AccommodationStatus.MAINTENANCE
                || request.getStatus() == AccommodationStatus.BLOCKED) {
            assertNoBlockingOccupancy(bedId);
        }
        bed.setStatus(request.getStatus());
        bed.setDefaultRent(request.getDefaultRent());
        bed.setDefaultDeposit(request.getDefaultDeposit());

        bed = bedRepository.save(bed);
        pricingPropagationService.propagateFrom(spaceId, bed);
        return BedResponse.from(bed);
    }

    @Transactional
    public void deactivateBedById(UUID spaceId, UUID bedId, UUID callerId) {
        BedEntity bed = bedRepository.findByIdAndSpaceId(bedId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Bed", bedId));
        deactivateBed(spaceId, bed.getRoom().getId(), bedId, callerId);
    }

    @Transactional
    public void deactivateBed(UUID spaceId, UUID roomId, UUID bedId, UUID callerId) {
        log.info("Deactivating bed: spaceId={}, roomId={}, bedId={}, callerId={}",
                spaceId, roomId, bedId, callerId);

        accessService.assertCanDeactivateStructure(spaceId, callerId);

        roomRepository.findActiveByIdAndSpaceId(roomId, spaceId)
                .orElseThrow(() -> ResourceNotFoundException.notInSpace("Room", roomId));

        BedEntity bed = bedRepository.findActiveByIdAndRoomId(bedId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Bed", "id", bedId));

        bed.setActive(false);
        bedRepository.save(bed);
    }

    private void assertNoBlockingOccupancy(UUID bedId) {
        if (occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)) {
            throw new BusinessException("Cannot change bed status while an active or reserved occupancy exists");
        }
    }
}
