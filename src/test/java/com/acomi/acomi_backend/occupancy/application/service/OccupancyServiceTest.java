package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.application.service.AccommodationAccessService;
import com.acomi.acomi_backend.accommodation.domain.model.AccommodationStatus;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.UnitEntity;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.meal.application.service.MealOccupancyBridgeService;
import com.acomi.acomi_backend.member.domain.model.MemberGender;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.notification.application.service.OccupancyNotificationSyncService;
import com.acomi.acomi_backend.occupancy.api.dto.request.AllocateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.CancelReservationRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.MoveInOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.ReserveOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.TransferOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.VacateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.application.support.StayAvailabilityRules;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyHistoryRepository;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.service.PaymentMonthSnapshotService;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService;
import com.acomi.acomi_backend.space.domain.model.GenderPolicy;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private OccupancyHistoryRepository occupancyHistoryRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OccupancyAccessService occupancyAccessService;

    @Mock
    private AccommodationAccessService accommodationAccessService;

    @Mock
    private OccupancyTargetService occupancyTargetService;

    @Mock
    private AccommodationStatusSyncService accommodationStatusSyncService;

    @Mock
    private GenderPolicyValidator genderPolicyValidator;

    @Mock
    private OccupancyContractSnapshotService contractSnapshotService;

    @Mock
    private MealOccupancyBridgeService mealOccupancyBridgeService;

    @Mock
    private OccupancyAmenityService occupancyAmenityService;

    @Mock
    private OccupancyNotificationSyncService occupancyNotificationSyncService;

    @Mock
    private SpacePaymentGenerationService paymentGenerationService;

    @Mock
    private PaymentMonthSnapshotService paymentMonthSnapshotService;

    @Mock
    private OccupancyExtensionService occupancyExtensionService;

    @Mock
    private StayRangeGuard stayRangeGuard;

    @InjectMocks
    private OccupancyService occupancyService;

    private UUID spaceId;
    private UUID callerId;
    private UUID memberId;
    private UUID bedId;
    private SpaceEntity space;
    private MemberEntity member;
    private UserEntity actor;
    private OccupancyTargetService.ResolvedTarget target;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        bedId = UUID.randomUUID();

        space = SpaceEntity.builder().type(SpaceType.PG).isActive(true).build();
        space.setId(spaceId);

        member = MemberEntity.builder()
                .fullName("Ravi")
                .mobileNumber("9999999999")
                .space(space)
                .gender(MemberGender.MALE)
                .build();
        member.setId(memberId);

        actor = UserEntity.builder().build();
        actor.setId(callerId);

        BuildingEntity building = BuildingEntity.builder().space(space).name("Block A").code("A").build();
        building.setId(UUID.randomUUID());
        FloorEntity floor = FloorEntity.builder().building(building).name("Floor 1").floorNumber(1).build();
        floor.setId(UUID.randomUUID());
        RoomEntity room = RoomEntity.builder()
                .floor(floor)
                .name("Room 101")
                .roomNumber("101")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        room.setId(UUID.randomUUID());
        BedEntity bed = BedEntity.builder()
                .room(room)
                .name("Bed A")
                .bedNumber("A")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        bed.setId(bedId);

        target = OccupancyTargetService.ResolvedTarget.builder()
                .targetType(AllocationTargetType.BED)
                .building(building)
                .floor(floor)
                .room(room)
                .bed(bed)
                .build();

        lenient()
                .doAnswer(invocation -> {
                    OccupancyEntity occ = invocation.getArgument(0);
                    occ.setRentSnapshot(new java.math.BigDecimal("8000"));
                    occ.setDepositSnapshot(java.math.BigDecimal.ZERO);
                    occ.setPricingLockedAt(java.time.LocalDateTime.now());
                    return null;
                })
                .when(contractSnapshotService)
                .applyActivationSnapshot(any(), any(), any(), any(), any());
        lenient()
                .when(contractSnapshotService.loadChargeSnapshots(any()))
                .thenReturn(java.util.List.of());
        lenient()
                .when(occupancyAmenityService.loadForOccupancy(any()))
                .thenReturn(java.util.List.of());
    }

    @Test
    void allocate_whenMemberAlreadyAllocated_throws() {
        AllocateOccupancyRequest request = allocateRequest();

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("active or reserved occupancy");

        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_whenRangeOverlaps_throwsWithoutSaving() {
        doThrow(new BusinessException("STAY_OVERLAP", StayAvailabilityRules.OVERLAP))
                .when(stayRangeGuard)
                .assertAvailable(
                        any(),
                        nullable(UUID.class),
                        nullable(UUID.class),
                        nullable(UUID.class),
                        any(),
                        nullable(LocalDate.class),
                        nullable(UUID.class));
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, allocateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_whenTargetAvailable_createsActiveOccupancyWithMoveInToday() {
        AllocateOccupancyRequest request = allocateRequest();

        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        assertThat(savedActiveOccupancy().getMoveInDate()).isEqualTo(LocalDate.now());
        verify(accommodationStatusSyncService).markOccupied(target);
        verify(contractSnapshotService).applyActivationSnapshot(any(), any(), eq(target), eq(space), any());
        verify(memberRepository).save(member);
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.ALLOCATED);
        verify(occupancyHistoryRepository).save(any());
        verify(genderPolicyValidator).validate(space, member);
        verify(paymentGenerationService).createActivationPayments(any(), eq(callerId));
        verify(occupancyNotificationSyncService).onAllocationCreated(any());
    }

    @Test
    void allocate_withExplicitHistoricalMoveInDate_persistsAndChecksThatRange() {
        LocalDate moveIn = LocalDate.now().minusDays(2);
        LocalDate plannedEnd = LocalDate.now().plusDays(3);
        AllocateOccupancyRequest request = allocateRequest();
        request.setMoveInDate(moveIn);
        request.setExpectedExitDate(plannedEnd);
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        assertThat(savedActiveOccupancy().getMoveInDate()).isEqualTo(moveIn);
        assertThat(savedActiveOccupancy().getExpectedExitDate()).isEqualTo(plannedEnd);
        verify(stayRangeGuard).assertAvailable(
                eq(AllocationTargetType.BED),
                eq(bedId),
                eq(target.getRoom().getId()),
                isNull(),
                eq(moveIn),
                eq(plannedEnd),
                isNull());
    }

    @Test
    void allocate_withPlannedEndOnMoveInDate_throwsWithoutSaving() {
        LocalDate moveIn = LocalDate.now().minusDays(1);
        AllocateOccupancyRequest request = allocateRequest();
        request.setMoveInDate(moveIn);
        request.setExpectedExitDate(moveIn);
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after the move-in date");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_withFutureMoveInDate_throwsUseReserve() {
        AllocateOccupancyRequest request = allocateRequest();
        request.setMoveInDate(LocalDate.now().plusDays(1));
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reserve the stay instead");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void reserve_createsReservedOccupancyAndSyncsBed() {
        ReserveOccupancyRequest request = new ReserveOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        request.setMoveInDate(LocalDate.now().plusDays(3));

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);
        when(occupancyTargetService.resolveForReserve(
                        eq(spaceId), eq(SpaceType.PG), eq(AllocationTargetType.BED), eq(bedId), eq(null), eq(null)))
                .thenReturn(target);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)).thenReturn(false);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> {
            OccupancyEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            assertThat(saved.getStatus()).isEqualTo(OccupancyStatus.RESERVED);
            assertThat(saved.getMoveInDate()).isEqualTo(request.getMoveInDate());
            assertThat(saved.getReservedAt()).isNotNull();
            return saved;
        });

        occupancyService.reserve(spaceId, callerId, request);

        verify(accommodationStatusSyncService).markReserved(target);
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.RESERVED);
        verify(paymentGenerationService, never()).createActivationPayments(any(), any());
    }

    @Test
    void reserve_whenBedOccupied_throws() {
        ReserveOccupancyRequest request = new ReserveOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        request.setMoveInDate(LocalDate.now().plusDays(1));

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyTargetService.resolveForReserve(
                        eq(spaceId), eq(SpaceType.PG), eq(AllocationTargetType.BED), eq(bedId), eq(null), eq(null)))
                .thenReturn(target);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> occupancyService.reserve(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bed is not available");
    }

    @Test
    void moveIn_whenBeforeMoveInDate_throws() {
        UUID occupancyId = UUID.randomUUID();
        MoveInOccupancyRequest request = new MoveInOccupancyRequest();

        OccupancyEntity occupancy = reservedOccupancy(occupancyId, LocalDate.now().plusDays(2));
        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)).thenReturn(Optional.of(occupancy));

        assertThatThrownBy(() -> occupancyService.moveIn(spaceId, occupancyId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Move-in date has not been reached");
    }

    @Test
    void moveIn_whenReserved_promotesToActive() {
        UUID occupancyId = UUID.randomUUID();
        MoveInOccupancyRequest request = new MoveInOccupancyRequest();

        OccupancyEntity occupancy = reservedOccupancy(occupancyId, LocalDate.now());
        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)).thenReturn(Optional.of(occupancy));
        when(occupancyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyService.moveIn(spaceId, occupancyId, callerId, request);

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getActualMoveInAt()).isNotNull();
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.ALLOCATED);
        verify(accommodationStatusSyncService).markOccupied(any());
        verify(paymentGenerationService).createActivationPayments(occupancy, callerId);
    }

    @Test
    void cancelReservation_releasesInventoryAndUpdatesMember() {
        UUID occupancyId = UUID.randomUUID();
        CancelReservationRequest request = new CancelReservationRequest();

        OccupancyEntity occupancy = reservedOccupancy(occupancyId, LocalDate.now().plusDays(2));
        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)).thenReturn(Optional.of(occupancy));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);

        occupancyService.cancelReservation(spaceId, occupancyId, callerId, request);

        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.VACATED);
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.VACATED);
        verify(accommodationStatusSyncService).releaseTarget(AllocationTargetType.BED, bedId, occupancy.getRoom().getId(), null);
        verify(occupancyNotificationSyncService).onReservationCancelled(occupancy);
    }

    @Test
    void allocate_whenGenderMismatch_throws() {
        space.setGenderPolicy(GenderPolicy.FEMALE);
        AllocateOccupancyRequest request = allocateRequest();

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        doThrow(new BusinessException("Member gender does not match space female-only policy"))
                .when(genderPolicyValidator)
                .validate(space, member);

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("female-only");
    }

    @Test
    void vacate_whenActive_releasesTargetAndMarksMemberVacated() {
        UUID occupancyId = UUID.randomUUID();
        VacateOccupancyRequest request = new VacateOccupancyRequest();
        request.setRemarks("Checkout completed");

        BedEntity bed = BedEntity.builder().name("Bed A").bedNumber("A").status(AccommodationStatus.OCCUPIED).build();
        bed.setId(bedId);
        RoomEntity room = RoomEntity.builder().name("Room 101").roomNumber("101").build();
        room.setId(UUID.randomUUID());
        bed.setRoom(room);

        BuildingEntity building = BuildingEntity.builder().space(space).name("Block A").code("A").build();
        building.setId(UUID.randomUUID());

        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(building)
                .room(room)
                .bed(bed)
                .allocatedBy(actor)
                .moveInDate(LocalDate.now())
                .status(OccupancyStatus.ACTIVE)
                .build();
        occupancy.setId(occupancyId);

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(occupancyRepository.findByIdAndSpaceIdForUpdate(occupancyId, spaceId)).thenReturn(Optional.of(occupancy));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);

        occupancyService.vacate(spaceId, occupancyId, callerId, request);

        verify(accommodationStatusSyncService)
                .releaseTarget(AllocationTargetType.BED, bedId, room.getId(), null);
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.VACATED);
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.VACATED);
        verify(occupancyNotificationSyncService).onMoveOutCompleted(occupancy);
    }

    @Test
    void allocate_bed_whenRoomTargetStayIsActive_throws() {
        stubAllocateLookups();
        when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                        target.getRoom().getId(), AllocationTargetType.ROOM, OccupancyStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, allocateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bed is not available");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void reserve_bed_whenRoomTargetStayIsReserved_throws() {
        ReserveOccupancyRequest request = new ReserveOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        request.setMoveInDate(LocalDate.now().plusDays(3));

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);
        when(occupancyTargetService.resolveForReserve(
                        eq(spaceId), eq(SpaceType.PG), eq(AllocationTargetType.BED), eq(bedId), eq(null), eq(null)))
                .thenReturn(target);
        lenient()
                .when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                        target.getRoom().getId(), AllocationTargetType.ROOM, OccupancyStatus.RESERVED))
                .thenReturn(true);

        assertThatThrownBy(() -> occupancyService.reserve(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bed is not available");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_room_whenBedTargetStayIsActive_throws() {
        space.setType(SpaceType.CO_LIVING);
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        AllocateOccupancyRequest request = allocateRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setBedId(null);
        request.setRoomId(roomTarget.getRoom().getId());
        stubMemberAndActor();
        when(occupancyTargetService.resolve(
                        eq(spaceId),
                        eq(SpaceType.CO_LIVING),
                        eq(AllocationTargetType.ROOM),
                        eq(null),
                        eq(roomTarget.getRoom().getId()),
                        eq(null)))
                .thenReturn(roomTarget);
        lenient()
                .when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                        roomTarget.getRoom().getId(), AllocationTargetType.BED, OccupancyStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Room is not available");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_secondBedInSameRoom_whenNoRoomTargetStay_succeeds() {
        UUID secondBedId = UUID.randomUUID();
        BedEntity secondBed = BedEntity.builder()
                .room(target.getRoom())
                .name("Bed B")
                .bedNumber("B")
                .status(AccommodationStatus.AVAILABLE)
                .build();
        secondBed.setId(secondBedId);
        OccupancyTargetService.ResolvedTarget secondTarget = OccupancyTargetService.ResolvedTarget.builder()
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(secondBed)
                .build();
        AllocateOccupancyRequest request = allocateRequest();
        request.setBedId(secondBedId);
        stubMemberAndActor();
        when(occupancyTargetService.resolve(
                        eq(spaceId), eq(SpaceType.PG), eq(AllocationTargetType.BED), eq(secondBedId), eq(null), eq(null)))
                .thenReturn(secondTarget);
        lenient()
                .when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                        target.getRoom().getId(), AllocationTargetType.BED, OccupancyStatus.ACTIVE))
                .thenReturn(true);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyService.allocate(spaceId, callerId, request);

        verify(accommodationStatusSyncService).markOccupied(secondTarget);
    }

    @Test
    void allocate_room_whenChildBedWouldBeMaintenanceOrBlocked_stillSucceeds() {
        space.setType(SpaceType.CO_LIVING);
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        AllocateOccupancyRequest request = allocateRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setBedId(null);
        request.setRoomId(roomTarget.getRoom().getId());
        stubMemberAndActor();
        when(occupancyTargetService.resolve(
                        eq(spaceId),
                        eq(SpaceType.CO_LIVING),
                        eq(AllocationTargetType.ROOM),
                        eq(null),
                        eq(roomTarget.getRoom().getId()),
                        eq(null)))
                .thenReturn(roomTarget);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getTargetType()).isEqualTo(AllocationTargetType.ROOM);
        verify(accommodationStatusSyncService).markOccupied(roomTarget);
    }

    @Test
    void allocate_rentalUnit_withNoBed_succeeds() {
        space.setType(SpaceType.RENTAL);
        UUID unitId = UUID.randomUUID();
        UnitEntity unit = UnitEntity.builder().name("Unit 1").unitNumber("1").status(AccommodationStatus.AVAILABLE).build();
        unit.setId(unitId);
        OccupancyTargetService.ResolvedTarget unitTarget = OccupancyTargetService.ResolvedTarget.builder()
                .targetType(AllocationTargetType.UNIT)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .unit(unit)
                .build();
        AllocateOccupancyRequest request = allocateRequest();
        request.setTargetType(AllocationTargetType.UNIT);
        request.setBedId(null);
        request.setUnitId(unitId);
        stubMemberAndActor();
        when(occupancyTargetService.resolve(
                        eq(spaceId), eq(SpaceType.RENTAL), eq(AllocationTargetType.UNIT), eq(null), eq(null), eq(unitId)))
                .thenReturn(unitTarget);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyService.allocate(spaceId, callerId, request);

        assertThat(savedActiveOccupancy().getBed()).isNull();
        verify(accommodationStatusSyncService).markOccupied(unitTarget);
    }

    @Test
    void transfer_bedToContainingRoom_excludesCurrentBedStay() {
        space.setType(SpaceType.CO_LIVING);
        UUID occupancyId = UUID.randomUUID();
        UUID roomId = target.getRoom().getId();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(roomId);
        stubTransfer(current, roomTarget, null, roomId, null);
        lenient()
                .when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                        roomId, AllocationTargetType.BED, OccupancyStatus.ACTIVE))
                .thenReturn(true);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        assertThat(current.getStatus()).isEqualTo(OccupancyStatus.VACATED);
        assertThat(savedActiveOccupancy().getTargetType()).isEqualTo(AllocationTargetType.ROOM);
        verify(accommodationStatusSyncService).markOccupied(roomTarget);
    }

    @Test
    void transfer_roomToItsBed_excludesCurrentRoomStay() {
        space.setType(SpaceType.CO_LIVING);
        UUID occupancyId = UUID.randomUUID();
        UUID roomId = target.getRoom().getId();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setTargetType(AllocationTargetType.ROOM);
        current.setBed(null);
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        stubTransfer(current, target, bedId, null, null);
        lenient()
                .when(occupancyRepository.existsByRoomIdAndTargetTypeAndStatus(
                        roomId, AllocationTargetType.ROOM, OccupancyStatus.ACTIVE))
                .thenReturn(true);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        assertThat(current.getStatus()).isEqualTo(OccupancyStatus.VACATED);
        assertThat(savedActiveOccupancy().getTargetType()).isEqualTo(AllocationTargetType.BED);
        verify(accommodationStatusSyncService).markOccupied(target);
    }

    @Test
    void transfer_copiesStayKindWithoutTurningItIntoMonthlyRent() {
        space.setType(SpaceType.CO_LIVING);
        UUID occupancyId = UUID.randomUUID();
        UUID roomId = target.getRoom().getId();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.SHORT);
        current.setPricingBasis(null);
        current.setAgreedTotal(new BigDecimal("5600"));
        current.setRentSnapshot(null);
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(roomId);
        stubTransfer(current, roomTarget, null, roomId, null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(saved.getAgreedTotal()).isEqualByComparingTo("5600");
        assertThat(saved.getRentSnapshot()).isNull();
    }

    @Test
    void transfer_destinationStayKindMonthlyClearsShortSource() {
        space.setType(SpaceType.CO_LIVING);
        space.setShortStayEnabled(false);
        UUID occupancyId = UUID.randomUUID();
        UUID roomId = target.getRoom().getId();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.SHORT);
        current.setAgreedTotal(new BigDecimal("5600"));
        current.setExpectedExitDate(LocalDate.now().plusDays(5));
        current.setExpectedCheckoutDate(LocalDate.now().plusDays(5));
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(roomId);
        request.setStayKind(StayKind.MONTHLY);
        stubTransfer(current, roomTarget, null, roomId, null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.MONTHLY);
        assertThat(saved.getAgreedTotal()).isNull();
        assertThat(saved.getPricingBasis()).isNull();
        assertThat(saved.getExpectedExitDate()).isNull();
    }

    @Test
    void transfer_destinationOpenEndedDoesNotKeepShortCheckout() {
        space.setType(SpaceType.PG);
        UUID occupancyId = UUID.randomUUID();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.SHORT);
        current.setAgreedTotal(new BigDecimal("4000"));
        current.setExpectedExitDate(LocalDate.now().plusDays(4));
        current.setExpectedCheckoutDate(LocalDate.now().plusDays(4));
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(target.getRoom().getId());
        request.setStayKind(StayKind.OPEN_ENDED);
        stubTransfer(current, roomTarget, null, target.getRoom().getId(), null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(saved.getAgreedTotal()).isNull();
        assertThat(saved.getExpectedExitDate()).isNull();
        assertThat(saved.getExpectedCheckoutDate()).isNull();
    }

    @Test
    void transfer_destinationShortPersistsAgreedTotalAndEnd() {
        space.setType(SpaceType.PG);
        space.setShortStayEnabled(false);
        UUID occupancyId = UUID.randomUUID();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.MONTHLY);
        current.setAgreedTotal(null);
        LocalDate checkout = LocalDate.now().plusDays(3);
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(target.getRoom().getId());
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("3200"));
        request.setExpectedExitDate(checkout);
        stubTransfer(current, roomTarget, null, target.getRoom().getId(), null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(saved.getAgreedTotal()).isEqualByComparingTo("3200");
        assertThat(saved.getPricingBasis()).isEqualTo("FIXED_TOTAL");
        assertThat(saved.getExpectedExitDate()).isEqualTo(checkout);
    }

    @Test
    void transfer_destinationStayKindOverridesSourceShortToMonthly() {
        space.setType(SpaceType.CO_LIVING);
        space.setShortStayEnabled(false);
        UUID occupancyId = UUID.randomUUID();
        UUID roomId = target.getRoom().getId();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.SHORT);
        current.setAgreedTotal(new BigDecimal("5600"));
        current.setExpectedExitDate(LocalDate.now().plusDays(5));
        current.setExpectedCheckoutDate(LocalDate.now().plusDays(5));
        OccupancyTargetService.ResolvedTarget roomTarget = roomTarget();
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(roomId);
        request.setStayKind(StayKind.MONTHLY);
        request.setRentSnapshot(new BigDecimal("9000"));
        stubTransfer(current, roomTarget, null, roomId, null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.MONTHLY);
        assertThat(saved.getAgreedTotal()).isNull();
        assertThat(saved.getPricingBasis()).isNull();
    }

    @Test
    void transfer_destinationOpenEndedClearsInheritedShortCheckout() {
        space.setType(SpaceType.PG);
        UUID occupancyId = UUID.randomUUID();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.SHORT);
        current.setAgreedTotal(new BigDecimal("4000"));
        current.setExpectedExitDate(LocalDate.now().plusDays(4));
        current.setExpectedCheckoutDate(LocalDate.now().plusDays(4));
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        request.setStayKind(StayKind.OPEN_ENDED);
        stubTransfer(current, target, bedId, null, null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(saved.getAgreedTotal()).isNull();
        assertThat(saved.getExpectedExitDate()).isNull();
        assertThat(saved.getExpectedCheckoutDate()).isNull();
    }

    @Test
    void transfer_destinationShortPersistsAgreedTotalAndExit() {
        space.setType(SpaceType.PG);
        space.setShortStayEnabled(false);
        UUID occupancyId = UUID.randomUUID();
        OccupancyEntity current = activeBedOccupancy(occupancyId);
        current.setStayKind(StayKind.MONTHLY);
        current.setRentSnapshot(new BigDecimal("8000"));
        LocalDate destEnd = LocalDate.now().plusDays(3);
        TransferOccupancyRequest request = new TransferOccupancyRequest();
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("3200"));
        request.setExpectedExitDate(destEnd);
        stubTransfer(current, target, bedId, null, null);

        occupancyService.transfer(spaceId, occupancyId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(saved.getAgreedTotal()).isEqualByComparingTo("3200");
        assertThat(saved.getPricingBasis()).isEqualTo("FIXED_TOTAL");
        assertThat(saved.getExpectedExitDate()).isEqualTo(destEnd);
    }

    @Test
    void allocate_onlyExpectedCheckoutDate_persistsBothFields() {
        LocalDate plannedEnd = LocalDate.of(2026, 12, 1);
        AllocateOccupancyRequest request = allocateRequest();
        request.setExpectedCheckoutDate(plannedEnd);
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getExpectedCheckoutDate()).isEqualTo(plannedEnd);
        assertThat(saved.getExpectedExitDate()).isEqualTo(plannedEnd);
    }

    @Test
    void allocate_onlyExpectedExitDate_persistsBothFields() {
        LocalDate plannedEnd = LocalDate.of(2026, 12, 2);
        AllocateOccupancyRequest request = allocateRequest();
        request.setExpectedExitDate(plannedEnd);
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getExpectedCheckoutDate()).isEqualTo(plannedEnd);
        assertThat(saved.getExpectedExitDate()).isEqualTo(plannedEnd);
    }

    @Test
    void allocate_equalPlannedEndFields_persistsThatDate() {
        LocalDate plannedEnd = LocalDate.of(2026, 12, 3);
        AllocateOccupancyRequest request = allocateRequest();
        request.setExpectedCheckoutDate(plannedEnd);
        request.setExpectedExitDate(plannedEnd);
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getExpectedCheckoutDate()).isEqualTo(plannedEnd);
        assertThat(saved.getExpectedExitDate()).isEqualTo(plannedEnd);
    }

    @Test
    void allocate_differentPlannedEndFields_throwsWithoutSaving() {
        AllocateOccupancyRequest request = allocateRequest();
        request.setExpectedCheckoutDate(LocalDate.of(2026, 12, 1));
        request.setExpectedExitDate(LocalDate.of(2026, 12, 4));
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expectedExitDate")
                .hasMessageContaining("expectedCheckoutDate");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_withoutPlannedEnd_keepsBothFieldsNull() {
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, allocateRequest());

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getExpectedCheckoutDate()).isNull();
        assertThat(saved.getExpectedExitDate()).isNull();
    }

    @Test
    void allocate_shortStay_storesFixedTotalAndDoesNotRequireMonthlyRent() {
        space.setShortStayEnabled(true);
        AllocateOccupancyRequest request = allocateRequest();
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setDepositSnapshot(new BigDecimal("2000"));
        request.setExpectedExitDate(LocalDate.now().plusDays(1));
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(saved.getAgreedTotal()).isEqualByComparingTo("5600");
        assertThat(saved.getPricingBasis()).isEqualTo("FIXED_TOTAL");
        assertThat(saved.getExpectedExitDate()).isEqualTo(LocalDate.now().plusDays(1));
        verify(paymentGenerationService).createActivationPayments(any(), eq(callerId));
    }

    @Test
    void allocate_shortStayWhenSpaceFlagOff_stillPersists() {
        // short_stay_enabled retained for compatibility; it no longer blocks SHORT create.
        space.setShortStayEnabled(false);
        AllocateOccupancyRequest request = allocateRequest();
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setExpectedExitDate(LocalDate.now().plusDays(1));
        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(saved.getAgreedTotal()).isEqualByComparingTo("5600");
        verify(paymentGenerationService).createActivationPayments(any(), eq(callerId));
    }

    @Test
    void allocate_shortStaySameDay_throwsWithoutSaving() {
        space.setShortStayEnabled(true);
        AllocateOccupancyRequest request = allocateRequest();
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setExpectedExitDate(LocalDate.now());
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("after the move-in date");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_shortStayAcrossMonths_throwsWithoutSaving() {
        space.setShortStayEnabled(true);
        AllocateOccupancyRequest request = allocateRequest();
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setExpectedExitDate(LocalDate.now().plusMonths(1).plusDays(2));
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("more than one calendar month");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_shortStayWithoutCheckout_throwsWithoutSaving() {
        space.setShortStayEnabled(true);
        AllocateOccupancyRequest request = allocateRequest();
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("planned checkout");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_monthlyWithAgreedTotal_throwsWithoutSaving() {
        AllocateOccupancyRequest request = allocateRequest();
        request.setAgreedTotal(new BigDecimal("5600"));
        stubAllocateLookups();

        assertThatThrownBy(() -> occupancyService.allocate(spaceId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only for a short stay");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void allocate_openEndedDailyRate_storesDailyRentAndActivatesPayments() {
        AllocateOccupancyRequest request = allocateRequest();
        request.setStayKind(StayKind.OPEN_ENDED);
        request.setRentSnapshot(new BigDecimal("250"));
        request.setDepositSnapshot(new BigDecimal("1000"));
        stubAllocateHappyPath();
        // Default stub forces monthly catalog 8000; Until I Leave must keep owner daily rent.
        org.mockito.Mockito.doAnswer(invocation -> {
                    OccupancyEntity occ = invocation.getArgument(0);
                    OccupancyContractSnapshotService.ContractSnapshotInput input = invocation.getArgument(1);
                    occ.setRentSnapshot(input.getRentSnapshot());
                    occ.setDepositSnapshot(
                            input.getDepositSnapshot() != null ? input.getDepositSnapshot() : BigDecimal.ZERO);
                    occ.setPricingLockedAt(java.time.LocalDateTime.now());
                    return null;
                })
                .when(contractSnapshotService)
                .applyActivationSnapshot(any(), any(), any(), any(), any());

        occupancyService.allocate(spaceId, callerId, request);

        OccupancyEntity saved = savedActiveOccupancy();
        assertThat(saved.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(saved.getPricingBasis()).isEqualTo("DAILY_RATE");
        assertThat(saved.getRentSnapshot()).isEqualByComparingTo("250");
        assertThat(saved.getAgreedTotal()).isNull();
        verify(paymentGenerationService).createActivationPayments(any(), eq(callerId));
    }

    @Test
    void reserve_shortStay_storesKindWithoutCreatingPayments() {
        // Flag off must not block stay-level SHORT reserve.
        space.setShortStayEnabled(false);
        ReserveOccupancyRequest request = new ReserveOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        LocalDate moveIn = LocalDate.now().plusDays(3);
        request.setMoveInDate(moveIn);
        request.setExpectedExitDate(moveIn.plusDays(1));
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);
        when(occupancyTargetService.resolveForReserve(
                        eq(spaceId), eq(SpaceType.PG), eq(AllocationTargetType.BED), eq(bedId), eq(null), eq(null)))
                .thenReturn(target);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)).thenReturn(false);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyService.reserve(spaceId, callerId, request);

        ArgumentCaptor<OccupancyEntity> captor = ArgumentCaptor.forClass(OccupancyEntity.class);
        verify(occupancyRepository).save(captor.capture());
        OccupancyEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OccupancyStatus.RESERVED);
        assertThat(saved.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(saved.getAgreedTotal()).isEqualByComparingTo("5600");
        verify(paymentGenerationService, never()).createActivationPayments(any(), any());
    }

    @Test
    void reserve_openEndedDailyRate_storesPricingWithoutCreatingPayments() {
        ReserveOccupancyRequest request = new ReserveOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        LocalDate moveIn = LocalDate.now().plusDays(5);
        request.setMoveInDate(moveIn);
        request.setStayKind(StayKind.OPEN_ENDED);
        request.setRentSnapshot(new BigDecimal("250"));
        request.setDepositSnapshot(new BigDecimal("1500"));

        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);
        when(occupancyTargetService.resolveForReserve(
                        eq(spaceId), eq(SpaceType.PG), eq(AllocationTargetType.BED), eq(bedId), eq(null), eq(null)))
                .thenReturn(target);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)).thenReturn(false);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        occupancyService.reserve(spaceId, callerId, request);

        ArgumentCaptor<OccupancyEntity> captor = ArgumentCaptor.forClass(OccupancyEntity.class);
        verify(occupancyRepository).save(captor.capture());
        OccupancyEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OccupancyStatus.RESERVED);
        assertThat(saved.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(saved.getPricingBasis()).isEqualTo("DAILY_RATE");
        assertThat(saved.getRentSnapshot()).isEqualByComparingTo("250");
        verify(paymentGenerationService, never()).createActivationPayments(any(), any());
    }

    private AllocateOccupancyRequest allocateRequest() {
        AllocateOccupancyRequest request = new AllocateOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        return request;
    }

    private void stubMemberAndActor() {
        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);
    }

    private OccupancyTargetService.ResolvedTarget roomTarget() {
        return OccupancyTargetService.ResolvedTarget.builder()
                .targetType(AllocationTargetType.ROOM)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .build();
    }

    private OccupancyEntity activeBedOccupancy(UUID occupancyId) {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(target.getBed())
                .allocatedBy(actor)
                .moveInDate(LocalDate.now())
                .status(OccupancyStatus.ACTIVE)
                .build();
        occupancy.setId(occupancyId);
        return occupancy;
    }

    private void stubTransfer(
            OccupancyEntity current,
            OccupancyTargetService.ResolvedTarget newTarget,
            UUID bedId,
            UUID roomId,
            UUID unitId) {
        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(occupancyRepository.findByIdAndSpaceId(current.getId(), spaceId)).thenReturn(Optional.of(current));
        when(occupancyTargetService.resolve(
                        eq(spaceId), eq(space.getType()), eq(newTarget.getTargetType()), eq(bedId), eq(roomId), eq(unitId)))
                .thenReturn(newTarget);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> {
            OccupancyEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    private OccupancyEntity savedActiveOccupancy() {
        ArgumentCaptor<OccupancyEntity> captor = ArgumentCaptor.forClass(OccupancyEntity.class);
        verify(occupancyRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(occupancy -> occupancy.getStatus() == OccupancyStatus.ACTIVE)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private void stubAllocateLookups() {
        stubMemberAndActor();
        when(occupancyTargetService.resolve(
                        eq(spaceId),
                        eq(SpaceType.PG),
                        eq(AllocationTargetType.BED),
                        eq(bedId),
                        eq(null),
                        eq(null)))
                .thenReturn(target);
        lenient().when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)).thenReturn(false);
        lenient().when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)).thenReturn(false);
    }

    private void stubAllocateHappyPath() {
        stubAllocateLookups();
        when(occupancyRepository.save(any())).thenAnswer(invocation -> {
            OccupancyEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            assertThat(saved.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
            assertThat(saved.getActualMoveInAt()).isNotNull();
            return saved;
        });
    }

    @Test
    void findCurrentOccupancySummary_exposesPersistedStayKind() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(target.getBed())
                .allocatedBy(actor)
                .moveInDate(LocalDate.now().minusDays(2))
                .status(OccupancyStatus.ACTIVE)
                .stayKind(StayKind.OPEN_ENDED)
                .build();
        occupancy.setId(UUID.randomUUID());

        when(occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId))
                .thenReturn(Optional.of(occupancy));

        var summary = occupancyService.findCurrentOccupancySummary(spaceId, memberId).orElseThrow();

        assertThat(summary.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(summary.getOccupancyId()).isEqualTo(occupancy.getId());
    }

    @Test
    void findCurrentOccupancySummary_nullStayKindForLegacyOccupancy() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(target.getBed())
                .allocatedBy(actor)
                .moveInDate(LocalDate.now().minusDays(2))
                .status(OccupancyStatus.ACTIVE)
                .stayKind(null)
                .build();
        occupancy.setId(UUID.randomUUID());

        when(occupancyRepository.findActiveBySpaceIdAndMemberId(spaceId, memberId))
                .thenReturn(Optional.of(occupancy));

        var summary = occupancyService.findCurrentOccupancySummary(spaceId, memberId).orElseThrow();

        assertThat(summary.getStayKind()).isNull();
    }

    @Test
    void findBedOccupant_exposesPersistedStayKind() {
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .bed(target.getBed())
                .allocatedBy(actor)
                .status(OccupancyStatus.ACTIVE)
                .stayKind(StayKind.SHORT)
                .build();
        occupancy.setId(UUID.randomUUID());

        when(occupancyRepository.findCurrentByBedId(bedId)).thenReturn(Optional.of(occupancy));

        var occupant = occupancyService.findBedOccupant(bedId).orElseThrow();

        assertThat(occupant.getStayKind()).isEqualTo(StayKind.SHORT);
        assertThat(occupant.getMemberId()).isEqualTo(memberId);
    }

    @Test
    void getMemberOccupancies_preservesHistoricalStayKindsPerOccupancy() {
        OccupancyEntity current = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(target.getBed())
                .allocatedBy(actor)
                .moveInDate(LocalDate.now().minusDays(10))
                .status(OccupancyStatus.ACTIVE)
                .stayKind(StayKind.MONTHLY)
                .build();
        current.setId(UUID.randomUUID());

        OccupancyEntity priorShort = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(target.getBed())
                .allocatedBy(actor)
                .moveInDate(LocalDate.now().minusMonths(3))
                .vacatedAt(LocalDate.now().minusMonths(2).atStartOfDay())
                .status(OccupancyStatus.VACATED)
                .stayKind(StayKind.SHORT)
                .build();
        priorShort.setId(UUID.randomUUID());

        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.findAllBySpaceIdAndMemberIdOrderByAllocatedAtDesc(spaceId, memberId))
                .thenReturn(java.util.List.of(current, priorShort));
        when(occupancyHistoryRepository.findBySpaceIdAndMemberIdOrderByPerformedAtDesc(spaceId, memberId))
                .thenReturn(java.util.List.of());

        var response = occupancyService.getMemberOccupancies(spaceId, memberId, callerId);

        assertThat(response.getCurrentOccupancy().getStayKind()).isEqualTo(StayKind.MONTHLY);
        assertThat(response.getOccupancies())
                .extracting(o -> o.getStayKind())
                .containsExactly(StayKind.MONTHLY, StayKind.SHORT);
    }

    private OccupancyEntity reservedOccupancy(UUID occupancyId, LocalDate moveInDate) {
        BedEntity bed = target.getBed();
        OccupancyEntity occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(target.getBuilding())
                .floor(target.getFloor())
                .room(target.getRoom())
                .bed(bed)
                .allocatedBy(actor)
                .moveInDate(moveInDate)
                .status(OccupancyStatus.RESERVED)
                .build();
        occupancy.setId(occupancyId);
        return occupancy;
    }
}
