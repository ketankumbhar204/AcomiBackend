package com.acomi.acomi_backend.occupancy.application.service;

import com.acomi.acomi_backend.accommodation.application.service.AccommodationAccessService;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.common.exception.ResourceNotFoundException;
import com.acomi.acomi_backend.common.web.PagedResponse;
import com.acomi.acomi_backend.meal.application.service.MealOccupancyBridgeService;
import com.acomi.acomi_backend.member.domain.model.MemberCategory;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.notification.application.service.OccupancyNotificationSyncService;
import com.acomi.acomi_backend.occupancy.api.dto.request.ExtendOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.response.ExtendOccupancyResponse;
import com.acomi.acomi_backend.occupancy.api.dto.request.AllocateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.CancelReservationRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.MoveInOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.ReserveOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.TransferOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.VacateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.response.BedOccupantSummaryResponse;
import com.acomi.acomi_backend.occupancy.api.dto.response.CurrentOccupancySummaryResponse;
import com.acomi.acomi_backend.occupancy.api.dto.response.MemberOccupancyListResponse;
import com.acomi.acomi_backend.occupancy.api.dto.response.OccupancyHistoryEntryResponse;
import com.acomi.acomi_backend.occupancy.api.dto.response.OccupancyResponse;
import com.acomi.acomi_backend.occupancy.api.dto.response.ReservationCancellationPreviewResponse;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.occupancy.application.support.ShortStayRules;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyHistoryEvent;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyHistoryEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyHistoryRepository;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.service.PaymentMonthSnapshotService;
import com.acomi.acomi_backend.payment.application.service.PaymentSettlementService;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService;
import com.acomi.acomi_backend.space.api.dto.AmenityAssignmentDto;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OccupancyService {

    private final OccupancyRepository occupancyRepository;
    private final OccupancyHistoryRepository occupancyHistoryRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OccupancyAccessService occupancyAccessService;
    private final AccommodationAccessService accommodationAccessService;
    private final OccupancyTargetService occupancyTargetService;
    private final AccommodationStatusSyncService accommodationStatusSyncService;
    private final GenderPolicyValidator genderPolicyValidator;
    private final OccupancyContractSnapshotService contractSnapshotService;
    private final MealOccupancyBridgeService mealOccupancyBridgeService;
    private final OccupancyAmenityService occupancyAmenityService;
    private final OccupancyNotificationSyncService occupancyNotificationSyncService;
    private final SpacePaymentGenerationService paymentGenerationService;
    private final PaymentSettlementService paymentSettlementService;
    private final PaymentMonthSnapshotService paymentMonthSnapshotService;
    private final OccupancyExtensionService occupancyExtensionService;
    private final StayRangeGuard stayRangeGuard;

    @Transactional
    public OccupancyResponse reserve(UUID spaceId, UUID callerId, ReserveOccupancyRequest request) {
        log.info("Reserving occupancy: spaceId={}, memberId={}, callerId={}", spaceId, request.getMemberId(), callerId);

        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        SpaceEntity space = accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = loadUser(callerId);

        MemberEntity member = loadActiveMember(spaceId, request.getMemberId());
        occupancyAccessService.assertSubjectIsResident(member);
        assertMemberHasNoCurrentOccupancy(spaceId, member.getId());
        validateMoveInDateForReserve(request.getMoveInDate());

        genderPolicyValidator.validate(space, member);

        OccupancyTargetService.ResolvedTarget target = occupancyTargetService.resolveForReserve(
                spaceId,
                space.getType(),
                request.getTargetType(),
                request.getBedId(),
                request.getRoomId(),
                request.getUnitId());
        assertTargetNotHeld(target, null, request.getMoveInDate(), request.getExpectedExitDate());

        LocalDateTime now = LocalDateTime.now();
        MemberCategory category = request.getMemberCategory() != null
                ? request.getMemberCategory()
                : member.getMemberCategory();

        OccupancyEntity occupancy = buildOccupancy(
                        space, member, target, actor, now, OccupancyStatus.RESERVED, request.getMoveInDate(), null)
                .reservedAt(now)
                .expectedExitDate(request.getExpectedExitDate())
                .memberCategory(category)
                .remarks(request.getRemarks())
                .build();
        applyStayChoice(
                space,
                occupancy,
                request.getStayKind(),
                request.getAgreedTotal(),
                request.getRentSnapshot(),
                request.getMoveInDate(),
                request.getExpectedExitDate());
        // Capture pricing on the reservation; activation payments are created only on move-in.
        // SHORT: rentSnapshot is the daily rate (stay rent = daily × nights stored in agreedTotal).
        if (request.getRentSnapshot() != null && occupancy.getRentSnapshot() == null) {
            occupancy.setRentSnapshot(request.getRentSnapshot());
        }
        if (request.getDepositSnapshot() != null) {
            occupancy.setDepositSnapshot(request.getDepositSnapshot());
        }
        if (request.getFoodEnabled() != null) {
            occupancy.setFoodEnabled(request.getFoodEnabled());
        }
        if (request.getFoodIncludedInRent() != null) {
            occupancy.setFoodIncludedInRent(request.getFoodIncludedInRent());
        }
        if (request.getFoodChargeSnapshot() != null) {
            occupancy.setFoodChargeSnapshot(request.getFoodChargeSnapshot());
        }

        BigDecimal bookingAdvance = request.getBookingAdvanceAmount();
        if (bookingAdvance != null && bookingAdvance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Booking advance cannot be negative");
        }
        if (bookingAdvance != null && bookingAdvance.compareTo(BigDecimal.ZERO) > 0) {
            occupancy.setBookingAdvanceAmount(bookingAdvance);
        }

        occupancy = occupancyRepository.save(occupancy);
        accommodationStatusSyncService.markReserved(target);
        member.setOccupancyStatus(MemberOccupancyStatus.RESERVED);
        memberRepository.save(member);

        if (bookingAdvance != null && bookingAdvance.compareTo(BigDecimal.ZERO) > 0) {
            boolean collected = Boolean.TRUE.equals(request.getBookingAdvanceCollected());
            paymentGenerationService.createReservationFirstPeriodRentSplit(
                    occupancy,
                    bookingAdvance,
                    collected,
                    request.getBookingAdvancePaymentMethod(),
                    callerId);
            LocalDate start = occupancy.getMoveInDate() != null ? occupancy.getMoveInDate() : LocalDate.now();
            paymentMonthSnapshotService.refreshAfterMutation(
                    spaceId, callerId, YearMonth.from(start).toString());
        }

        recordHistory(occupancy, OccupancyHistoryEvent.RESERVED, null, targetSnapshot(target), actor, now, request.getRemarks());
        occupancyNotificationSyncService.onReservationCreated(occupancy);
        return toResponse(occupancy);
    }

    @Transactional
    public OccupancyResponse moveIn(UUID spaceId, UUID occupancyId, UUID callerId, MoveInOccupancyRequest request) {
        log.info("Move-in occupancy: spaceId={}, occupancyId={}, callerId={}", spaceId, occupancyId, callerId);

        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        SpaceEntity space = accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = loadUser(callerId);

        MoveInOccupancyRequest body = request != null ? request : new MoveInOccupancyRequest();
        OccupancyEntity occupancy = loadReservedOccupancy(spaceId, occupancyId);
        occupancyAccessService.assertSubjectIsResident(occupancy.getMember());

        LocalDate scheduledMoveIn = occupancy.getMoveInDate();
        LocalDate effectiveMoveIn = body.getMoveInDate() != null ? body.getMoveInDate() : scheduledMoveIn;
        if (!body.isAllowEarlyMoveIn() && effectiveMoveIn.isAfter(LocalDate.now())) {
            throw new BusinessException("Move-in date has not been reached");
        }

        genderPolicyValidator.validate(occupancy.getSpace(), occupancy.getMember());

        LocalDateTime now = LocalDateTime.now();
        occupancy.setStatus(OccupancyStatus.ACTIVE);
        occupancy.setMoveInDate(effectiveMoveIn);
        occupancy.setActualMoveInAt(now);
        if (body.getExpectedExitDate() != null) {
            occupancy.setExpectedExitDate(body.getExpectedExitDate());
            occupancy.setExpectedCheckoutDate(body.getExpectedExitDate());
        }
        if (body.getAgreementSigned() != null) {
            occupancy.setAgreementSigned(body.getAgreementSigned());
        }
        occupancy.setUpdatedBy(actor);
        if (body.getRemarks() != null && !body.getRemarks().isBlank()) {
            occupancy.setRemarks(body.getRemarks());
        }
        applyStayChoice(
                space,
                occupancy,
                body.getStayKind(),
                body.getAgreedTotal(),
                body.getRentSnapshot(),
                effectiveMoveIn,
                occupancy.getExpectedExitDate());
        stayRangeGuard.assertAvailable(
                occupancy.getTargetType(),
                occupancy.getBed() != null ? occupancy.getBed().getId() : null,
                occupancy.getRoom() != null ? occupancy.getRoom().getId() : null,
                occupancy.getUnit() != null ? occupancy.getUnit().getId() : null,
                effectiveMoveIn,
                occupancy.getExpectedExitDate(),
                occupancy.getId());

        OccupancyTargetService.ResolvedTarget target = toResolvedTarget(occupancy);
        contractSnapshotService.applyActivationSnapshot(
                occupancy, toContractInput(body), target, occupancy.getSpace(), now);
        occupancy = occupancyRepository.save(occupancy);
        occupancyAmenityService.applyToOccupancy(occupancy, body.getAmenities(), null);

        accommodationStatusSyncService.markOccupied(target);

        MemberEntity member = occupancy.getMember();
        member.setOccupancyStatus(MemberOccupancyStatus.ALLOCATED);
        memberRepository.save(member);

        recordHistory(occupancy, OccupancyHistoryEvent.MOVE_IN, targetSnapshot(occupancy), targetSnapshot(occupancy), actor, now, body.getRemarks());
        mealOccupancyBridgeService.onOccupancyActivated(occupancy, actor, body.isCreateMealParticipation());
        syncActivationPayments(occupancy, callerId);
        occupancyNotificationSyncService.onMoveInCompleted(occupancy);
        return toResponse(occupancy);
    }

    @Transactional(readOnly = true)
    public ReservationCancellationPreviewResponse previewReservationCancellation(
            UUID spaceId, UUID occupancyId, UUID callerId) {
        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        accommodationAccessService.loadAccommodationSpace(spaceId);
        OccupancyEntity occupancy = loadReservedOccupancy(spaceId, occupancyId);
        return paymentSettlementService.previewReservationCancellation(spaceId, occupancy, callerId);
    }

    @Transactional
    public OccupancyResponse cancelReservation(
            UUID spaceId, UUID occupancyId, UUID callerId, CancelReservationRequest request) {
        log.info("Cancel reservation: spaceId={}, occupancyId={}, callerId={}", spaceId, occupancyId, callerId);

        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = loadUser(callerId);

        CancelReservationRequest body = request != null ? request : new CancelReservationRequest();
        OccupancyEntity occupancy = loadReservedOccupancy(spaceId, occupancyId);
        TargetSnapshot fromTarget = targetSnapshot(occupancy);
        LocalDateTime now = LocalDateTime.now();

        // Settle / cancel reservation payment obligations before closing the occupancy.
        paymentSettlementService.settleReservationCancellation(
                spaceId,
                occupancy,
                callerId,
                body.getSettlements(),
                body.getRemarks() != null && !body.getRemarks().isBlank()
                        ? body.getRemarks()
                        : "Reservation cancelled");

        closeOccupancy(occupancy, actor, now, body.getRemarks());
        // Rebuild financial snapshot after VACATED so Expected does not reinflate for
        // never-moved-in cancellations on a later ensureMonth heal.
        String snapshotMonth = occupancy.getMoveInDate() != null
                ? java.time.YearMonth.from(occupancy.getMoveInDate()).toString()
                : java.time.YearMonth.from(now).toString();
        paymentSettlementService.refreshMonthSnapshot(spaceId, callerId, snapshotMonth);
        accommodationStatusSyncService.releaseTarget(
                fromTarget.targetType(),
                fromTarget.bedId(),
                fromTarget.roomId(),
                fromTarget.unitId());

        refreshMemberOccupancyStatus(occupancy.getMember(), spaceId);

        recordHistory(occupancy, OccupancyHistoryEvent.RESERVATION_CANCELLED, fromTarget, null, actor, now, body.getRemarks());
        occupancyNotificationSyncService.onReservationCancelled(occupancy);
        return toResponse(occupancy);
    }

    @Transactional
    public OccupancyResponse allocate(UUID spaceId, UUID callerId, AllocateOccupancyRequest request) {
        log.info("Allocating member: spaceId={}, memberId={}, callerId={}, targetType={}",
                spaceId, request.getMemberId(), callerId, request.getTargetType());

        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        SpaceEntity space = accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = loadUser(callerId);

        MemberEntity member = loadActiveMember(spaceId, request.getMemberId());
        occupancyAccessService.assertSubjectIsResident(member);
        assertMemberHasNoCurrentOccupancy(spaceId, member.getId());
        genderPolicyValidator.validate(space, member);

        OccupancyTargetService.ResolvedTarget target = occupancyTargetService.resolve(
                spaceId,
                space.getType(),
                request.getTargetType(),
                request.getBedId(),
                request.getRoomId(),
                request.getUnitId());

        LocalDate today = LocalDate.now();
        LocalDate moveInDate = resolveAllocateMoveInDate(request, today);
        LocalDateTime now = LocalDateTime.now();
        LocalDate expectedExit = resolveAllocatePlannedEnd(request);
        validatePlannedEndAfterStart(moveInDate, expectedExit);
        assertTargetNotHeld(target, null, moveInDate, expectedExit);

        OccupancyEntity occupancy = buildOccupancy(space, member, target, actor, now, OccupancyStatus.ACTIVE, moveInDate, now)
                .expectedCheckoutDate(expectedExit)
                .expectedExitDate(expectedExit)
                .remarks(request.getRemarks())
                .build();
        applyStayChoice(
                space,
                occupancy,
                request.getStayKind(),
                request.getAgreedTotal(),
                request.getRentSnapshot(),
                moveInDate,
                expectedExit);

        occupancy = occupancyRepository.save(occupancy);
        contractSnapshotService.applyActivationSnapshot(
                occupancy, toContractInput(request), target, space, now);
        occupancy = occupancyRepository.save(occupancy);
        occupancyAmenityService.applyToOccupancy(occupancy, request.getAmenities(), null);
        accommodationStatusSyncService.markOccupied(target);
        member.setOccupancyStatus(MemberOccupancyStatus.ALLOCATED);
        memberRepository.save(member);

        recordHistory(occupancy, OccupancyHistoryEvent.ALLOCATED, null, targetSnapshot(target), actor, now, request.getRemarks());
        mealOccupancyBridgeService.onOccupancyActivated(occupancy, actor, request.isCreateMealParticipation());
        syncActivationPayments(occupancy, callerId);
        occupancyNotificationSyncService.onAllocationCreated(occupancy);
        return toResponse(occupancy);
    }

    @Transactional
    public OccupancyResponse transfer(
            UUID spaceId, UUID occupancyId, UUID callerId, TransferOccupancyRequest request) {
        log.info("Transferring occupancy: spaceId={}, occupancyId={}, callerId={}", spaceId, occupancyId, callerId);

        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        SpaceEntity space = accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = loadUser(callerId);

        OccupancyEntity current = loadActiveOccupancy(spaceId, occupancyId);
        occupancyAccessService.assertSubjectIsResident(current.getMember());
        genderPolicyValidator.validate(space, current.getMember());
        TargetSnapshot fromTarget = targetSnapshot(current);

        OccupancyTargetService.ResolvedTarget newTarget = occupancyTargetService.resolve(
                spaceId,
                space.getType(),
                request.getTargetType(),
                request.getBedId(),
                request.getRoomId(),
                request.getUnitId());

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        assertTargetNotHeld(newTarget, current.getId(), today, null);
        closeOccupancy(current, actor, now, request.getRemarks());
        accommodationStatusSyncService.releaseTarget(
                fromTarget.targetType(),
                fromTarget.bedId(),
                fromTarget.roomId(),
                fromTarget.unitId());

        OccupancyEntity.OccupancyEntityBuilder nextBuilder = buildOccupancy(
                        space,
                        current.getMember(),
                        newTarget,
                        actor,
                        now,
                        OccupancyStatus.ACTIVE,
                        today,
                        now)
                .remarks(request.getRemarks());

        OccupancyEntity next;
        if (request.getStayKind() != null) {
            // Destination stay type wins. Do not inherit a Short Stay checkout onto OPEN_ENDED.
            LocalDate destEnd = request.getExpectedExitDate();
            if (request.getStayKind() == StayKind.SHORT && destEnd == null) {
                destEnd = current.getExpectedExitDate();
            }
            if (request.getStayKind() == StayKind.OPEN_ENDED) {
                destEnd = request.getExpectedExitDate(); // null unless owner sends one
            }
            nextBuilder.expectedExitDate(destEnd).expectedCheckoutDate(destEnd);
            next = nextBuilder.build();
            applyStayChoice(
                    space,
                    next,
                    request.getStayKind(),
                    request.getAgreedTotal(),
                    request.getRentSnapshot(),
                    today,
                    destEnd);
            if (request.getStayKind() != StayKind.SHORT) {
                // Destination left short-stay pricing; do not keep source agreed total.
                next.setAgreedTotal(null);
                // applyStayChoice already set MONTHLY→null or OPEN_ENDED→DAILY_RATE.
            }
        } else {
            next = nextBuilder
                    .stayKind(current.getStayKind())
                    .pricingBasis(current.getPricingBasis())
                    .agreedTotal(current.getAgreedTotal())
                    .expectedExitDate(current.getExpectedExitDate())
                    .expectedCheckoutDate(current.getExpectedCheckoutDate())
                    .build();
        }

        next = occupancyRepository.save(next);
        contractSnapshotService.applyTransferSnapshot(
                next, toContractInput(request), request.getRentPolicy(), current, newTarget, space, now);
        next = occupancyRepository.save(next);
        occupancyAmenityService.applyToOccupancy(
                next,
                request.getAmenities(),
                occupancyAmenityService.loadForOccupancy(current.getId()));
        accommodationStatusSyncService.markOccupied(newTarget);

        recordHistory(
                next,
                OccupancyHistoryEvent.TRANSFERRED,
                fromTarget,
                targetSnapshot(newTarget),
                actor,
                now,
                request.getRemarks());

        occupancyNotificationSyncService.onOccupancyTransferred(next);
        return toResponse(next);
    }

    @Transactional
    public OccupancyResponse vacate(
            UUID spaceId, UUID occupancyId, UUID callerId, VacateOccupancyRequest request) {
        log.info("Vacating occupancy: spaceId={}, occupancyId={}, callerId={}", spaceId, occupancyId, callerId);

        occupancyAccessService.assertCanManageOccupancy(spaceId, callerId);
        accommodationAccessService.loadAccommodationSpace(spaceId);
        UserEntity actor = loadUser(callerId);

        OccupancyEntity occupancy = loadActiveOccupancyForUpdate(spaceId, occupancyId);
        occupancyAccessService.assertSubjectIsResident(occupancy.getMember());
        TargetSnapshot fromTarget = targetSnapshot(occupancy);
        LocalDateTime now = LocalDateTime.now();

        closeOccupancy(occupancy, actor, now, request.getRemarks());
        accommodationStatusSyncService.releaseTarget(
                fromTarget.targetType(),
                fromTarget.bedId(),
                fromTarget.roomId(),
                fromTarget.unitId());

        // OPEN_ENDED daily-rate: close period immediately and bill actual days through vacate.
        paymentGenerationService.settleOpenEndedRentOnVacate(occupancy);
        LocalDate vacatedDate =
                occupancy.getVacatedAt() != null ? occupancy.getVacatedAt().toLocalDate() : null;
        if (vacatedDate != null && occupancy.getSpace() != null) {
            paymentMonthSnapshotService.refreshAfterMutation(
                    occupancy.getSpace().getId(),
                    callerId,
                    YearMonth.from(vacatedDate).toString());
        }

        refreshMemberOccupancyStatus(occupancy.getMember(), spaceId);
        mealOccupancyBridgeService.onVacate(occupancy.getMember(), actor);

        recordHistory(
                occupancy,
                OccupancyHistoryEvent.VACATED,
                fromTarget,
                null,
                actor,
                now,
                request.getRemarks());

        occupancyNotificationSyncService.onMoveOutCompleted(occupancy);
        return toResponse(occupancy);
    }

    @Transactional(readOnly = true)
    public OccupancyResponse getOccupancy(UUID spaceId, UUID occupancyId, UUID callerId) {
        OccupancyEntity occupancy = occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Occupancy", "id", occupancyId));

        occupancyAccessService.assertCanViewMemberOccupancy(
                spaceId, occupancy.getMember().getId(), callerId, occupancy.getMember());
        return toResponse(occupancy);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OccupancyResponse> listSpaceOccupancies(
            UUID spaceId,
            UUID callerId,
            OccupancyStatus status,
            UUID memberId,
            UUID buildingId,
            UUID floorId,
            UUID unitId,
            UUID roomId,
            UUID bedId,
            AllocationTargetType targetType,
            Pageable pageable) {
        occupancyAccessService.assertCanViewSpaceOccupancies(spaceId, callerId);

        Page<OccupancyEntity> page = occupancyRepository.search(
                spaceId, status, memberId, buildingId, floorId, unitId, roomId, bedId, targetType, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public MemberOccupancyListResponse getMemberOccupancies(UUID spaceId, UUID memberId, UUID callerId) {
        MemberEntity member = memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));

        occupancyAccessService.assertCanViewMemberOccupancy(spaceId, memberId, callerId, member);

        List<OccupancyEntity> occupancies =
                occupancyRepository.findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(spaceId, memberId);
        List<OccupancyHistoryEntryResponse> history = occupancyHistoryRepository
                .findBySpaceIdAndMemberIdOrderByPerformedAtDesc(spaceId, memberId)
                .stream()
                .map(OccupancyHistoryEntryResponse::from)
                .toList();

        Optional<OccupancyEntity> active = occupancies.stream()
                .filter(o -> o.getStatus() == OccupancyStatus.ACTIVE)
                .findFirst();
        Optional<OccupancyEntity> reserved = occupancies.stream()
                .filter(o -> o.getStatus() == OccupancyStatus.RESERVED)
                .findFirst();

        return MemberOccupancyListResponse.builder()
                .currentOccupancy(active.map(this::toResponse).orElse(null))
                .reservedOccupancy(reserved.map(this::toResponse).orElse(null))
                .occupancies(occupancies.stream().map(this::toResponse).toList())
                .history(history)
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<CurrentOccupancySummaryResponse> findCurrentOccupancySummary(UUID spaceId, UUID memberId) {
        Optional<OccupancyEntity> active = occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId);
        if (active.isPresent()) {
            return Optional.of(toSummary(active.get()));
        }
        return occupancyRepository.findReservedBySpaceIdAndMemberId(spaceId, memberId).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public List<AmenityAssignmentDto> findAssignedAmenitiesForMember(UUID spaceId, UUID memberId) {
        Optional<OccupancyEntity> active = occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId);
        if (active.isPresent()) {
            return occupancyAmenityService.loadForOccupancy(active.get().getId());
        }
        return occupancyRepository
                .findReservedBySpaceIdAndMemberId(spaceId, memberId)
                .map(occupancy -> occupancyAmenityService.loadForOccupancy(occupancy.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Optional<BedOccupantSummaryResponse> findBedOccupant(UUID bedId) {
        return occupancyRepository.findCurrentByBedId(bedId).map(occupancy -> BedOccupantSummaryResponse.builder()
                .occupancyId(occupancy.getId())
                .memberId(occupancy.getMember().getId())
                .memberName(occupancy.getMember().getFullName())
                .occupancyStatus(occupancy.getStatus())
                .stayKind(occupancy.getStayKind())
                .build());
    }

    private void syncActivationPayments(OccupancyEntity occupancy, UUID callerId) {
        paymentGenerationService.createActivationPayments(occupancy, callerId);
        LocalDate start = occupancy.getActualMoveInAt() != null
                ? occupancy.getActualMoveInAt().toLocalDate()
                : occupancy.getMoveInDate();
        if (start == null || occupancy.getSpace() == null) {
            return;
        }
        paymentMonthSnapshotService.refreshAfterMutation(
                occupancy.getSpace().getId(), callerId, YearMonth.from(start).toString());
    }

    private OccupancyEntity.OccupancyEntityBuilder buildOccupancy(
            SpaceEntity space,
            MemberEntity member,
            OccupancyTargetService.ResolvedTarget target,
            UserEntity actor,
            LocalDateTime allocatedAt,
            OccupancyStatus status,
            LocalDate moveInDate,
            LocalDateTime actualMoveInAt) {
        return OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(target.getTargetType())
                .building(target.getBuilding())
                .floor(target.getFloor())
                .unit(target.getUnit())
                .room(target.getRoom())
                .bed(target.getBed())
                .allocatedAt(allocatedAt)
                .allocatedBy(actor)
                .moveInDate(moveInDate)
                .actualMoveInAt(actualMoveInAt)
                .status(status)
                .createdBy(actor)
                .updatedBy(actor);
    }

    private CurrentOccupancySummaryResponse toSummary(OccupancyEntity occupancy) {
        return CurrentOccupancySummaryResponse.builder()
                .occupancyId(occupancy.getId())
                .occupancyStatus(occupancy.getStatus())
                .stayKind(occupancy.getStayKind())
                .targetType(occupancy.getTargetType())
                .buildingId(occupancy.getBuilding().getId())
                .buildingName(occupancy.getBuilding().getName())
                .floorId(occupancy.getFloor() != null ? occupancy.getFloor().getId() : null)
                .floorName(occupancy.getFloor() != null ? occupancy.getFloor().getName() : null)
                .unitId(occupancy.getUnit() != null ? occupancy.getUnit().getId() : null)
                .unitName(occupancy.getUnit() != null ? occupancy.getUnit().getName() : null)
                .roomId(occupancy.getRoom() != null ? occupancy.getRoom().getId() : null)
                .roomName(occupancy.getRoom() != null ? occupancy.getRoom().getName() : null)
                .bedId(occupancy.getBed() != null ? occupancy.getBed().getId() : null)
                .bedName(occupancy.getBed() != null ? occupancy.getBed().getName() : null)
                .moveInDate(occupancy.getMoveInDate())
                .build();
    }

    private OccupancyTargetService.ResolvedTarget toResolvedTarget(OccupancyEntity occupancy) {
        return OccupancyTargetService.ResolvedTarget.builder()
                .targetType(occupancy.getTargetType())
                .building(occupancy.getBuilding())
                .floor(occupancy.getFloor())
                .unit(occupancy.getUnit())
                .room(occupancy.getRoom())
                .bed(occupancy.getBed())
                .build();
    }

    private MemberEntity loadActiveMember(UUID spaceId, UUID memberId) {
        return memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", "id", memberId));
    }

    private void assertMemberHasNoCurrentOccupancy(UUID spaceId, UUID memberId) {
        if (occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE)
                || occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED)) {
            throw new BusinessException("Member already has an active or reserved occupancy in this space");
        }
    }

    private void validateMoveInDateForReserve(LocalDate moveInDate) {
        if (moveInDate.isBefore(LocalDate.now())) {
            throw new BusinessException("Move-in date cannot be in the past");
        }
    }

    @Transactional
    public ExtendOccupancyResponse extend(
            UUID spaceId, UUID occupancyId, UUID callerId, ExtendOccupancyRequest request) {
        OccupancyExtensionService.Result result =
                occupancyExtensionService.extend(spaceId, occupancyId, callerId, request);
        return ExtendOccupancyResponse.builder()
                .occupancy(toResponse(result.occupancy()))
                .chargeId(result.chargeId())
                .build();
    }

    private OccupancyEntity loadActiveOccupancyForUpdate(UUID spaceId, UUID occupancyId) {
        OccupancyEntity occupancy = occupancyRepository.findByIdAndSpaceIdForUpdate(occupancyId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Occupancy", "id", occupancyId));
        if (occupancy.getStatus() != OccupancyStatus.ACTIVE) {
            throw new BusinessException("Occupancy is not active");
        }
        return occupancy;
    }

    private OccupancyEntity loadActiveOccupancy(UUID spaceId, UUID occupancyId) {
        OccupancyEntity occupancy = occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Occupancy", "id", occupancyId));
        if (occupancy.getStatus() != OccupancyStatus.ACTIVE) {
            throw new BusinessException("Occupancy is not active");
        }
        return occupancy;
    }

    private OccupancyEntity loadReservedOccupancy(UUID spaceId, UUID occupancyId) {
        OccupancyEntity occupancy = occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Occupancy", "id", occupancyId));
        if (occupancy.getStatus() != OccupancyStatus.RESERVED) {
            throw new BusinessException("Occupancy is not reserved");
        }
        return occupancy;
    }

    private void closeOccupancy(OccupancyEntity occupancy, UserEntity actor, LocalDateTime now, String remarks) {
        occupancy.setStatus(OccupancyStatus.VACATED);
        occupancy.setVacatedAt(now);
        occupancy.setVacatedBy(actor);
        occupancy.setUpdatedBy(actor);
        if (remarks != null && !remarks.isBlank()) {
            occupancy.setRemarks(remarks);
        }
        occupancyRepository.save(occupancy);
    }

    private void refreshMemberOccupancyStatus(MemberEntity member, UUID spaceId) {
        if (occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, member.getId(), OccupancyStatus.ACTIVE)) {
            member.setOccupancyStatus(MemberOccupancyStatus.ALLOCATED);
        } else if (occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(
                spaceId, member.getId(), OccupancyStatus.RESERVED)) {
            member.setOccupancyStatus(MemberOccupancyStatus.RESERVED);
        } else {
            member.setOccupancyStatus(MemberOccupancyStatus.VACATED);
        }
        memberRepository.save(member);
    }

    private void applyStayChoice(
            SpaceEntity space,
            OccupancyEntity occupancy,
            StayKind requestedKind,
            BigDecimal requestedAgreedTotal,
            BigDecimal rentSnapshot,
            LocalDate start,
            LocalDate plannedEnd) {
        StayKind kind = requestedKind != null ? requestedKind : occupancy.getStayKind();
        BigDecimal agreed = requestedAgreedTotal != null ? requestedAgreedTotal : occupancy.getAgreedTotal();
        if (kind == null) {
            if (requestedAgreedTotal != null) {
                throw new BusinessException("AGREED_TOTAL_NOT_ALLOWED", ShortStayRules.AGREED_TOTAL_NOT_ALLOWED);
            }
            return;
        }
        if (kind == StayKind.MONTHLY) {
            if (requestedAgreedTotal != null) {
                throw new BusinessException("AGREED_TOTAL_NOT_ALLOWED", ShortStayRules.AGREED_TOTAL_NOT_ALLOWED);
            }
            occupancy.setStayKind(StayKind.MONTHLY);
            occupancy.setAgreedTotal(null);
            occupancy.setPricingBasis(null);
            return;
        }
        if (kind == StayKind.OPEN_ENDED) {
            if (requestedAgreedTotal != null) {
                throw new BusinessException("AGREED_TOTAL_NOT_ALLOWED", ShortStayRules.AGREED_TOTAL_NOT_ALLOWED);
            }
            // New Until I Leave uses owner-entered daily rent in rent_snapshot.
            // Legacy OPEN_ENDED rows keep pricing_basis null and monthly rent_snapshot meaning.
            occupancy.setStayKind(StayKind.OPEN_ENDED);
            occupancy.setAgreedTotal(null);
            occupancy.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
            return;
        }
        if (kind != StayKind.SHORT) {
            if (requestedAgreedTotal != null) {
                throw new BusinessException("AGREED_TOTAL_NOT_ALLOWED", ShortStayRules.AGREED_TOTAL_NOT_ALLOWED);
            }
            occupancy.setStayKind(kind);
            return;
        }
        // Stay type is per occupancy; spaces.short_stay_enabled is not an eligibility gate.
        // Prefer owner daily rate in rentSnapshot; agreedTotal becomes daily × nights (or legacy fixed).
        BigDecimal stayRent = ShortStayRules.resolveStayRent(start, plannedEnd, rentSnapshot, agreed);
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setAgreedTotal(stayRent);
        occupancy.setPricingBasis(OccupancyPricingBasis.FIXED_TOTAL);
        if (rentSnapshot != null) {
            occupancy.setRentSnapshot(rentSnapshot);
        }
    }

    private LocalDate resolveAllocatePlannedEnd(AllocateOccupancyRequest request) {
        LocalDate checkout = request.getExpectedCheckoutDate();
        LocalDate exit = request.getExpectedExitDate();
        if (checkout != null && exit != null && !checkout.equals(exit)) {
            throw new BusinessException("expectedExitDate and expectedCheckoutDate must be the same planned end date");
        }
        return checkout != null ? checkout : exit;
    }

    private LocalDate resolveAllocateMoveInDate(AllocateOccupancyRequest request, LocalDate today) {
        LocalDate moveInDate = request.getMoveInDate() != null ? request.getMoveInDate() : today;
        if (moveInDate.isAfter(today)) {
            throw new BusinessException("Move-in date cannot be in the future for an active allocation. Reserve the stay instead.");
        }
        return moveInDate;
    }

    private void validatePlannedEndAfterStart(LocalDate start, LocalDate plannedEnd) {
        if (start != null && plannedEnd != null && !plannedEnd.isAfter(start)) {
            throw new BusinessException("Planned end date must be after the move-in date");
        }
    }

    private void assertTargetNotHeld(
            OccupancyTargetService.ResolvedTarget target,
            UUID excludeOccupancyId,
            LocalDate start,
            LocalDate exclusiveEnd) {
        switch (target.getTargetType()) {
            case BED -> {
                UUID bedId = target.getBed().getId();
                if (bedHeld(bedId, OccupancyStatus.ACTIVE, excludeOccupancyId)
                        || bedHeld(bedId, OccupancyStatus.RESERVED, excludeOccupancyId)) {
                    throw new BusinessException("Bed is not available");
                }
                if (target.getRoom() != null
                        && (roomTargetHeld(target.getRoom().getId(), OccupancyStatus.ACTIVE, excludeOccupancyId)
                                || roomTargetHeld(
                                        target.getRoom().getId(), OccupancyStatus.RESERVED, excludeOccupancyId))) {
                    throw new BusinessException("Bed is not available");
                }
            }
            case ROOM -> {
                UUID roomId = target.getRoom().getId();
                if (roomTargetHeld(roomId, OccupancyStatus.ACTIVE, excludeOccupancyId)
                        || roomTargetHeld(roomId, OccupancyStatus.RESERVED, excludeOccupancyId)
                        || bedTargetInRoom(roomId, OccupancyStatus.ACTIVE, excludeOccupancyId)
                        || bedTargetInRoom(roomId, OccupancyStatus.RESERVED, excludeOccupancyId)) {
                    throw new BusinessException("Room is not available");
                }
            }
            case UNIT -> {
                UUID unitId = target.getUnit().getId();
                if (unitHeld(unitId, OccupancyStatus.ACTIVE, excludeOccupancyId)
                        || unitHeld(unitId, OccupancyStatus.RESERVED, excludeOccupancyId)) {
                    throw new BusinessException("Unit is not available");
                }
            }
        }
        stayRangeGuard.assertAvailable(
                target.getTargetType(),
                target.getBed() != null ? target.getBed().getId() : null,
                target.getRoom() != null ? target.getRoom().getId() : null,
                target.getUnit() != null ? target.getUnit().getId() : null,
                start,
                exclusiveEnd,
                excludeOccupancyId);
    }

    private boolean bedHeld(UUID bedId, OccupancyStatus status, UUID excludeOccupancyId) {
        if (excludeOccupancyId == null) {
            return occupancyRepository.existsByBedIdAndStatus(bedId, status);
        }
        return occupancyRepository.existsByBedIdAndStatusAndIdNot(bedId, status, excludeOccupancyId);
    }

    private boolean roomTargetHeld(UUID roomId, OccupancyStatus status, UUID excludeOccupancyId) {
        if (excludeOccupancyId == null) {
            return occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                    roomId, AllocationTargetType.ROOM, status);
        }
        return occupancyRepository.existsByRoomIdAndTargetTypeAndStatusAndIdNot(
                roomId, AllocationTargetType.ROOM, status, excludeOccupancyId);
    }

    private boolean bedTargetInRoom(UUID roomId, OccupancyStatus status, UUID excludeOccupancyId) {
        if (excludeOccupancyId == null) {
            return occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                    roomId, AllocationTargetType.BED, status);
        }
        return occupancyRepository.existsByRoomIdAndTargetTypeAndStatusAndIdNot(
                roomId, AllocationTargetType.BED, status, excludeOccupancyId);
    }

    private boolean unitHeld(UUID unitId, OccupancyStatus status, UUID excludeOccupancyId) {
        if (excludeOccupancyId == null) {
            return occupancyRepository.existsByUnitIdAndStatus(unitId, status);
        }
        return occupancyRepository.existsByUnitIdAndStatusAndIdNot(unitId, status, excludeOccupancyId);
    }

    private void recordHistory(
            OccupancyEntity occupancy,
            OccupancyHistoryEvent eventType,
            TargetSnapshot from,
            TargetSnapshot to,
            UserEntity actor,
            LocalDateTime performedAt,
            String remarks) {
        OccupancyHistoryEntity history = OccupancyHistoryEntity.builder()
                .occupancy(occupancy)
                .space(occupancy.getSpace())
                .member(occupancy.getMember())
                .eventType(eventType)
                .fromTargetType(from != null ? from.targetType() : null)
                .fromBuildingId(from != null ? from.buildingId() : null)
                .fromFloorId(from != null ? from.floorId() : null)
                .fromUnitId(from != null ? from.unitId() : null)
                .fromRoomId(from != null ? from.roomId() : null)
                .fromBedId(from != null ? from.bedId() : null)
                .toTargetType(to != null ? to.targetType() : null)
                .toBuildingId(to != null ? to.buildingId() : null)
                .toFloorId(to != null ? to.floorId() : null)
                .toUnitId(to != null ? to.unitId() : null)
                .toRoomId(to != null ? to.roomId() : null)
                .toBedId(to != null ? to.bedId() : null)
                .performedBy(actor)
                .performedAt(performedAt)
                .remarks(remarks)
                .build();
        occupancyHistoryRepository.save(history);
    }

    private TargetSnapshot targetSnapshot(OccupancyTargetService.ResolvedTarget target) {
        return new TargetSnapshot(
                target.getTargetType(),
                target.getBuilding().getId(),
                target.getFloor() != null ? target.getFloor().getId() : null,
                target.getUnit() != null ? target.getUnit().getId() : null,
                target.getRoom() != null ? target.getRoom().getId() : null,
                target.getBed() != null ? target.getBed().getId() : null);
    }

    private TargetSnapshot targetSnapshot(OccupancyEntity occupancy) {
        return new TargetSnapshot(
                occupancy.getTargetType(),
                occupancy.getBuilding().getId(),
                occupancy.getFloor() != null ? occupancy.getFloor().getId() : null,
                occupancy.getUnit() != null ? occupancy.getUnit().getId() : null,
                occupancy.getRoom() != null ? occupancy.getRoom().getId() : null,
                occupancy.getBed() != null ? occupancy.getBed().getId() : null);
    }

    private UserEntity loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private OccupancyResponse toResponse(OccupancyEntity occupancy) {
        if (occupancy.getFloor() != null) {
            Hibernate.initialize(occupancy.getFloor());
        }
        if (occupancy.getUnit() != null) {
            Hibernate.initialize(occupancy.getUnit());
        }
        return OccupancyResponse.from(
                occupancy,
                contractSnapshotService.loadChargeSnapshots(occupancy),
                occupancyAmenityService.loadForOccupancy(occupancy.getId()));
    }

    private OccupancyContractSnapshotService.ContractSnapshotInput toContractInput(AllocateOccupancyRequest request) {
        return OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                .rentSnapshot(request.getRentSnapshot())
                .depositSnapshot(request.getDepositSnapshot())
                .foodEnabled(request.getFoodEnabled())
                .foodChargeSnapshot(request.getFoodChargeSnapshot())
                .foodIncludedInRent(request.getFoodIncludedInRent())
                .otherCharges(request.getOtherCharges())
                .build();
    }

    private OccupancyContractSnapshotService.ContractSnapshotInput toContractInput(MoveInOccupancyRequest request) {
        return OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                .rentSnapshot(request.getRentSnapshot())
                .depositSnapshot(request.getDepositSnapshot())
                .foodEnabled(request.getFoodEnabled())
                .foodChargeSnapshot(request.getFoodChargeSnapshot())
                .foodIncludedInRent(request.getFoodIncludedInRent())
                .otherCharges(request.getOtherCharges())
                .build();
    }

    private OccupancyContractSnapshotService.ContractSnapshotInput toContractInput(TransferOccupancyRequest request) {
        return OccupancyContractSnapshotService.ContractSnapshotInput.builder()
                .rentSnapshot(request.getRentSnapshot())
                .depositSnapshot(request.getDepositSnapshot())
                .foodEnabled(request.getFoodEnabled())
                .foodChargeSnapshot(request.getFoodChargeSnapshot())
                .foodIncludedInRent(request.getFoodIncludedInRent())
                .otherCharges(request.getOtherCharges())
                .build();
    }

    private record TargetSnapshot(
            AllocationTargetType targetType,
            UUID buildingId,
            UUID floorId,
            UUID unitId,
            UUID roomId,
            UUID bedId) {}
}
