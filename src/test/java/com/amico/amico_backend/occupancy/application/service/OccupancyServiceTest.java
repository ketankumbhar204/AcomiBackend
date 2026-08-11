package com.amico.amico_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amico.amico_backend.accommodation.application.service.AccommodationAccessService;
import com.amico.amico_backend.accommodation.domain.model.AccommodationStatus;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BedEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.FloorEntity;
import com.amico.amico_backend.accommodation.infrastructure.persistence.entity.RoomEntity;
import com.amico.amico_backend.common.exception.BusinessException;
import com.amico.amico_backend.meal.application.service.MealOccupancyBridgeService;
import com.amico.amico_backend.member.domain.model.MemberGender;
import com.amico.amico_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.amico.amico_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.amico.amico_backend.notification.application.service.OccupancyNotificationSyncService;
import com.amico.amico_backend.occupancy.api.dto.request.AllocateOccupancyRequest;
import com.amico.amico_backend.occupancy.api.dto.request.CancelReservationRequest;
import com.amico.amico_backend.occupancy.api.dto.request.MoveInOccupancyRequest;
import com.amico.amico_backend.occupancy.api.dto.request.ReserveOccupancyRequest;
import com.amico.amico_backend.occupancy.api.dto.request.VacateOccupancyRequest;
import com.amico.amico_backend.occupancy.domain.model.AllocationTargetType;
import com.amico.amico_backend.occupancy.domain.model.MemberOccupancyStatus;
import com.amico.amico_backend.occupancy.domain.model.OccupancyStatus;
import com.amico.amico_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyHistoryRepository;
import com.amico.amico_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.amico.amico_backend.space.domain.model.GenderPolicy;
import com.amico.amico_backend.space.domain.model.SpaceType;
import com.amico.amico_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.amico.amico_backend.user.infrastructure.persistence.entity.UserEntity;
import com.amico.amico_backend.user.infrastructure.persistence.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
    void allocate_whenTargetAvailable_createsActiveOccupancyWithMoveInToday() {
        AllocateOccupancyRequest request = allocateRequest();

        stubAllocateHappyPath();

        occupancyService.allocate(spaceId, callerId, request);

        verify(accommodationStatusSyncService).markOccupied(target);
        verify(contractSnapshotService).applyActivationSnapshot(any(), any(), eq(target), eq(space), any());
        verify(memberRepository).save(member);
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.ALLOCATED);
        verify(occupancyHistoryRepository).save(any());
        verify(genderPolicyValidator).validate(space, member);
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
        when(occupancyRepository.findByIdAndSpaceId(occupancyId, spaceId)).thenReturn(Optional.of(occupancy));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);

        occupancyService.vacate(spaceId, occupancyId, callerId, request);

        verify(accommodationStatusSyncService)
                .releaseTarget(AllocationTargetType.BED, bedId, room.getId(), null);
        assertThat(member.getOccupancyStatus()).isEqualTo(MemberOccupancyStatus.VACATED);
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.VACATED);
    }

    private AllocateOccupancyRequest allocateRequest() {
        AllocateOccupancyRequest request = new AllocateOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        return request;
    }

    private void stubAllocateHappyPath() {
        when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        when(memberRepository.findByIdAndSpaceIdAndActiveTrue(memberId, spaceId)).thenReturn(Optional.of(member));
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.ACTIVE))
                .thenReturn(false);
        when(occupancyRepository.existsBySpaceIdAndMemberIdAndStatus(spaceId, memberId, OccupancyStatus.RESERVED))
                .thenReturn(false);
        when(occupancyTargetService.resolve(
                        eq(spaceId),
                        eq(SpaceType.PG),
                        eq(AllocationTargetType.BED),
                        eq(bedId),
                        eq(null),
                        eq(null)))
                .thenReturn(target);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.ACTIVE)).thenReturn(false);
        when(occupancyRepository.existsByBedIdAndStatus(bedId, OccupancyStatus.RESERVED)).thenReturn(false);
        when(occupancyRepository.save(any())).thenAnswer(invocation -> {
            OccupancyEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            assertThat(saved.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
            assertThat(saved.getMoveInDate()).isEqualTo(LocalDate.now());
            assertThat(saved.getActualMoveInAt()).isNotNull();
            return saved;
        });
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
