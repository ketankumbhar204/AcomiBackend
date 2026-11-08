package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.SpaceMembershipEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentStatus;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.space.infrastructure.persistence.repository.SpaceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpacePaymentGenerationServiceTest {

    @Mock
    private SpaceRepository spaceRepository;

    @Mock
    private SpacePaymentRepository paymentRepository;

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private MealParticipationRepository participationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PayPerMealBillingCalculator payPerMealBillingCalculator;

    @Mock
    private MealBillingResolver mealBillingResolver;

    @Mock
    private SpacePaymentTimelineService timelineService;

    @Mock
    private SpacePaymentAccessService accessService;

    @Mock
    private OccupancyTargetLabelBuilder occupancyTargetLabelBuilder;

    @InjectMocks
    private SpacePaymentGenerationService generationService;

    private UUID spaceId;
    private UUID memberId;
    private UUID callerId;
    private SpaceEntity space;
    private MemberEntity member;
    private OccupancyEntity occupancy;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        callerId = UUID.randomUUID();

        space = SpaceEntity.builder().type(SpaceType.PG).billingDueDay(1).taxEnabled(false).build();
        space.setId(spaceId);

        member = MemberEntity.builder().fullName("Rahul Kumar").space(space).build();
        member.setId(memberId);

        occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.of(2026, 9, 1))
                .actualMoveInAt(LocalDateTime.of(2026, 9, 1, 10, 0))
                .rentSnapshot(new BigDecimal("3400"))
                .depositSnapshot(new BigDecimal("3000"))
                .foodEnabled(false)
                .foodIncludedInRent(false)
                .build();
        occupancy.setId(UUID.randomUUID());

        lenient()
                .when(occupancyTargetLabelBuilder.build(any()))
                .thenReturn("Bed A");
        lenient()
                .when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(paymentRepository.save(any())).thenAnswer(invocation -> {
            SpacePaymentEntity saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void createActivationPayments_createsRentAndOneTimeDeposit() {
        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        List<SpacePaymentEntity> saved = captor.getAllValues();
        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getAmount()).isEqualByComparingTo("3400");
                    assertThat(payment.getMonth()).isEqualTo("2026-09");
                });
        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.SECURITY);
                    assertThat(payment.getAmount()).isEqualByComparingTo("3000");
                    assertThat(payment.getMonth()).isEqualTo("2026-09");
                });
    }

    @Test
    void syncExpectedPayments_doesNotCreateDepositAfterMoveInMonth() {
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 10));

        verify(paymentRepository, never())
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-10",
                        SpacePaymentType.DEPOSIT,
                        SpacePaymentCategory.SECURITY);
        verify(paymentRepository)
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-10",
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.MONTHLY);
    }

    @Test
    void createActivationPayments_shortStayCreatesOneExtraAndOneDeposit() {
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setRentSnapshot(null);
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        occupancy.setDepositSnapshot(new BigDecimal("2000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 11, 1));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 8));
        when(paymentRepository
                        .findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
                                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneSatisfy(payment -> assertThat(payment.getPaymentCategory())
                        .isEqualTo(SpacePaymentCategory.MONTHLY));
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.EXTRA);
                    assertThat(payment.getAmount()).isEqualByComparingTo("5600");
                    assertThat(payment.getMonth()).isEqualTo("2026-11");
                    assertThat(payment.getBillingPeriodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
                    assertThat(payment.getBillingPeriodEnd()).isEqualTo(LocalDate.of(2026, 11, 8));
                    assertThat(payment.getBillableDays()).isEqualTo(7);
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                });
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.SECURITY);
                    assertThat(payment.getAmount()).isEqualByComparingTo("2000");
                });
    }

    @Test
    void createActivationPayments_shortStaySecondSyncDoesNotCreateAnotherCharge() {
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setRentSnapshot(null);
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        occupancy.setDepositSnapshot(new BigDecimal("2000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 11, 1));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 8));
        SpacePaymentEntity existingExtra = SpacePaymentEntity.builder()
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .amount(new BigDecimal("5600"))
                .billingPeriodStart(LocalDate.of(2026, 11, 1))
                .billingPeriodEnd(LocalDate.of(2026, 11, 8))
                .build();
        SpacePaymentEntity existingDeposit = SpacePaymentEntity.builder()
                .member(member)
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("2000"))
                .month("2026-11")
                .build();
        when(paymentRepository
                        .findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
                                occupancy.getId(),
                                SpacePaymentType.RENT,
                                SpacePaymentCategory.EXTRA,
                                LocalDate.of(2026, 11, 1),
                                LocalDate.of(2026, 11, 8)))
                .thenReturn(Optional.of(existingExtra));
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-11",
                        SpacePaymentType.DEPOSIT,
                        SpacePaymentCategory.SECURITY))
                .thenReturn(Optional.of(existingDeposit));

        generationService.createActivationPayments(occupancy, callerId);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createActivationPayments_shortStaySameDayWritesNothing() {
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setRentSnapshot(null);
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        occupancy.setMoveInDate(LocalDate.of(2026, 11, 1));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 1));

        assertThatThrownBy(() -> generationService.createActivationPayments(occupancy, callerId))
                .isInstanceOf(BusinessException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createActivationPayments_shortStayAcrossMonthsWritesNothing() {
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setRentSnapshot(null);
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        occupancy.setMoveInDate(LocalDate.of(2026, 1, 28));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 1, 28, 10, 0));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 2, 3));

        assertThatThrownBy(() -> generationService.createActivationPayments(occupancy, callerId))
                .isInstanceOf(BusinessException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void syncExpectedPayments_shortStayCreatesNoMonthlyRent() {
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setRentSnapshot(new BigDecimal("5600"));
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 11));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void syncExpectedPayments_doesNotUpdatePaidMonthlyRent() {
        occupancy.setRentSnapshot(new BigDecimal("10000"));
        SpacePaymentEntity paid = SpacePaymentEntity.builder()
                .member(member)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("10000"))
                .month("2026-11")
                .build();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId, memberId, "2026-11", SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY))
                .thenReturn(Optional.of(paid));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 11));

        verify(paymentRepository, never()).save(any());
        assertThat(paid.getAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void syncExpectedPayments_doesNotRepricePendingRentBeforeTheEffectiveMonth() {
        occupancy.setRentSnapshot(new BigDecimal("11000"));
        occupancy.setPriorRentSnapshot(new BigDecimal("10000"));
        occupancy.setRentEffectiveMonth(LocalDate.of(2026, 11, 1));
        SpacePaymentEntity october = SpacePaymentEntity.builder()
                .member(member)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("10000"))
                .month("2026-10")
                .build();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId, memberId, "2026-10", SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY))
                .thenReturn(Optional.of(october));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 10));

        verify(paymentRepository, never()).save(october);
        assertThat(october.getAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void syncExpectedPayments_updatesPendingRentInTheEffectiveMonth() {
        occupancy.setRentSnapshot(new BigDecimal("11000"));
        occupancy.setPriorRentSnapshot(new BigDecimal("10000"));
        occupancy.setRentEffectiveMonth(LocalDate.of(2026, 11, 1));
        SpacePaymentEntity november = SpacePaymentEntity.builder()
                .member(member)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("10000"))
                .month("2026-11")
                .dueDate(LocalDate.of(2026, 11, 1))
                .title("Rent")
                .build();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId, memberId, "2026-11", SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY))
                .thenReturn(Optional.of(november));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 11));

        verify(paymentRepository).save(november);
        assertThat(november.getAmount()).isEqualByComparingTo("11000");
    }

    @Test
    void createReservationFirstPeriodRentSplit_createsAdvanceAndRemaining() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setDepositSnapshot(new BigDecimal("6000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(null);
        occupancy.setBookingAdvanceAmount(new BigDecimal("2000"));

        generationService.createReservationFirstPeriodRentSplit(
                occupancy, new BigDecimal("2000"), false, null, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        List<SpacePaymentEntity> saved = captor.getAllValues();

        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.ADVANCE);
                    assertThat(payment.getAmount()).isEqualByComparingTo("2000");
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                    assertThat(payment.getTitle()).contains("Booking advance");
                });
        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.MONTHLY);
                    assertThat(payment.getAmount()).isEqualByComparingTo("4000");
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                    assertThat(payment.getTitle()).contains("Remaining balance");
                });
        BigDecimal rentTotal = saved.stream()
                .filter(p -> p.getPaymentType() == SpacePaymentType.RENT)
                .map(SpacePaymentEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(rentTotal).isEqualByComparingTo("6000");
        assertThat(saved)
                .noneSatisfy(payment -> assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT));
    }

    @Test
    void createReservationFirstPeriodRentSplit_marksAdvancePaidWhenCollected() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(null);

        generationService.createReservationFirstPeriodRentSplit(
                occupancy,
                new BigDecimal("2000"),
                true,
                com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod.UPI,
                callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.ADVANCE);
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
                    assertThat(payment.getPaymentMethod())
                            .isEqualTo(com.acomi.acomi_backend.payment.domain.model.SpacePaymentMethod.UPI);
                });
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.MONTHLY);
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                });
    }

    @Test
    void createReservationFirstPeriodRentSplit_fullAdvanceCreatesNoRemaining() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(null);

        generationService.createReservationFirstPeriodRentSplit(
                occupancy, new BigDecimal("6000"), false, null, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPaymentCategory()).isEqualTo(SpacePaymentCategory.ADVANCE);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("6000");
    }

    @Test
    void createReservationFirstPeriodRentSplit_zeroAdvanceCreatesNothing() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));

        generationService.createReservationFirstPeriodRentSplit(
                occupancy, BigDecimal.ZERO, false, null, callerId);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createReservationFirstPeriodRentSplit_rejectsAdvanceAboveRent() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));

        assertThatThrownBy(() -> generationService.createReservationFirstPeriodRentSplit(
                        occupancy, new BigDecimal("7000"), false, null, callerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createReservationFirstPeriodRentSplit_allowsOpenEndedDailyAdvance() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis("DAILY_RATE");
        occupancy.setRentSnapshot(new BigDecimal("500"));
        occupancy.setMoveInDate(LocalDate.of(2026, 11, 10));
        occupancy.setActualMoveInAt(null);

        generationService.createReservationFirstPeriodRentSplit(
                occupancy, new BigDecimal("500"), true, null, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getPaymentCategory()).isEqualTo(SpacePaymentCategory.ADVANCE);
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("500");
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
    }

    @Test
    void createActivationPayments_skipsRentWhenBookingAdvanceExists() {
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setDepositSnapshot(new BigDecimal("5000"));
        occupancy.setBookingAdvanceAmount(new BigDecimal("2000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 12, 1, 10, 0));

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneSatisfy(payment -> assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT));
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
                    assertThat(payment.getAmount()).isEqualByComparingTo("5000");
                });
    }

    @Test
    void createActivationPayments_secondCallWithAdvanceStillSkipsRent() {
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setDepositSnapshot(new BigDecimal("5000"));
        occupancy.setBookingAdvanceAmount(new BigDecimal("2000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 12, 1, 10, 0));
        SpacePaymentEntity existingDeposit = SpacePaymentEntity.builder()
                .member(member)
                .paymentType(SpacePaymentType.DEPOSIT)
                .paymentCategory(SpacePaymentCategory.SECURITY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("5000"))
                .month("2026-12")
                .build();
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-12",
                        SpacePaymentType.DEPOSIT,
                        SpacePaymentCategory.SECURITY))
                .thenReturn(Optional.of(existingDeposit));

        generationService.createActivationPayments(occupancy, callerId);
        generationService.createActivationPayments(occupancy, callerId);

        verify(paymentRepository, never())
                .findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId,
                        memberId,
                        "2026-12",
                        SpacePaymentType.RENT,
                        SpacePaymentCategory.MONTHLY);
    }

    @Test
    void syncExpectedPayments_doesNotOverwriteReservationRemainingBalance() {
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setBookingAdvanceAmount(new BigDecimal("2000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 12, 1, 10, 0));
        SpacePaymentEntity remaining = SpacePaymentEntity.builder()
                .space(space)
                .member(member)
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("4000"))
                .month("2026-12")
                .dueDate(LocalDate.of(2026, 12, 1))
                .title("Rent — Remaining balance — December 2026")
                .build();
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(accessService.requireActiveMembership(spaceId, callerId))
                .thenReturn(SpaceMembershipEntity.builder().build());
        when(accessService.isOwnScopeOnly(any())).thenReturn(false);
        when(occupancyRepository.findBillableBySpaceIdForMonth(eq(spaceId), any(), any(), any()))
                .thenReturn(List.of(occupancy));
        lenient()
                .when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId, memberId, "2026-12", SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY))
                .thenReturn(Optional.of(remaining));

        generationService.syncExpectedPayments(spaceId, callerId, YearMonth.of(2026, 12));

        verify(paymentRepository, never()).save(remaining);
        assertThat(remaining.getAmount()).isEqualByComparingTo("4000");
    }

    @Test
    void cancelReservationRentObligations_rejectsUnpaidAndKeepsPaidAdvance() {
        occupancy.setBookingAdvanceAmount(new BigDecimal("2000"));
        SpacePaymentEntity paidAdvance = SpacePaymentEntity.builder()
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.ADVANCE)
                .paymentStatus(SpacePaymentStatus.PAID)
                .amount(new BigDecimal("2000"))
                .remarks("Reservation booking advance")
                .build();
        paidAdvance.setId(UUID.randomUUID());
        SpacePaymentEntity pendingRemaining = SpacePaymentEntity.builder()
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .paymentStatus(SpacePaymentStatus.PENDING)
                .amount(new BigDecimal("4000"))
                .build();
        pendingRemaining.setId(UUID.randomUUID());
        when(paymentRepository.findByOccupancy_Id(occupancy.getId()))
                .thenReturn(List.of(paidAdvance, pendingRemaining));

        generationService.cancelReservationRentObligations(occupancy);

        assertThat(paidAdvance.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PAID);
        assertThat(paidAdvance.getRemarks()).contains("Reservation cancelled");
        assertThat(pendingRemaining.getPaymentStatus()).isEqualTo(SpacePaymentStatus.REJECTED);
        assertThat(pendingRemaining.getRejectionReason()).isEqualTo("Reservation cancelled");
        verify(paymentRepository, times(2)).save(any());
    }

    @Test
    void isAccommodationApplicable_includesCorporateStayViaReservationSplit() {
        space.setType(SpaceType.CORPORATE_STAY);
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setActualMoveInAt(null);

        generationService.createReservationFirstPeriodRentSplit(
                occupancy, new BigDecimal("2000"), false, null, callerId);

        verify(paymentRepository, times(2)).save(any());
    }

    @Test
    void createReservationFirstPeriodRentSplit_shortStayUsesExtraRemaining() {
        occupancy.setStatus(OccupancyStatus.RESERVED);
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setRentSnapshot(new BigDecimal("1000"));
        occupancy.setAgreedTotal(new BigDecimal("5000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 12, 1));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 12, 6));
        occupancy.setActualMoveInAt(null);
        when(paymentRepository
                        .findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
                                any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        generationService.createReservationFirstPeriodRentSplit(
                occupancy, new BigDecimal("2000"), false, null, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.ADVANCE);
                    assertThat(payment.getAmount()).isEqualByComparingTo("2000");
                });
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.EXTRA);
                    assertThat(payment.getAmount()).isEqualByComparingTo("3000");
                    assertThat(payment.getTitle()).contains("Remaining balance");
                });
    }

    @Test
    void ensureAutomaticMonthlyRent_createsFullMonthDueOnTheFirst() {
        space.setBillingDueDay(5);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setMoveInDate(LocalDate.of(2026, 10, 15));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 10, 15, 11, 0));

        var result = generationService.ensureAutomaticMonthlyRent(space, occupancy, YearMonth.of(2026, 12));

        assertThat(result.write()).isEqualTo(SpacePaymentGenerationService.MonthlyRentWrite.CREATED);
        assertThat(result.payment().getAmount()).isEqualByComparingTo("6000");
        assertThat(result.payment().getDueDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(result.payment().getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
        assertThat(result.payment().getMonth()).isEqualTo("2026-12");
        assertThat(result.payment().getRemarks())
                .isEqualTo(SpacePaymentGenerationService.AUTOMATIC_CALENDAR_MONTH_RENT);
    }

    @Test
    void ensureAutomaticMonthlyRent_doesNotInsertWhenThePeriodAlreadyExists() {
        occupancy.setStayKind(StayKind.MONTHLY);
        SpacePaymentEntity existing = SpacePaymentEntity.builder()
                .paymentStatus(SpacePaymentStatus.PENDING)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .month("2026-11")
                .amount(new BigDecimal("3000"))
                .build();
        existing.setId(UUID.randomUUID());
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        spaceId, memberId, "2026-11", SpacePaymentType.RENT, SpacePaymentCategory.MONTHLY))
                .thenReturn(Optional.of(existing));

        var result = generationService.ensureAutomaticMonthlyRent(space, occupancy, YearMonth.of(2026, 11));

        assertThat(result.write()).isEqualTo(SpacePaymentGenerationService.MonthlyRentWrite.ALREADY_PRESENT);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void ensureAutomaticMonthlyRent_doesNotRecreateACancelledObligation() {
        occupancy.setStayKind(StayKind.MONTHLY);
        SpacePaymentEntity cancelled = SpacePaymentEntity.builder()
                .paymentStatus(SpacePaymentStatus.CANCELLED)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.MONTHLY)
                .month("2026-11")
                .amount(new BigDecimal("6000"))
                .build();
        when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        any(), any(), eq("2026-11"), any(), any()))
                .thenReturn(Optional.of(cancelled));

        var result = generationService.ensureAutomaticMonthlyRent(space, occupancy, YearMonth.of(2026, 11));

        assertThat(result.write()).isEqualTo(SpacePaymentGenerationService.MonthlyRentWrite.FROZEN);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void ensureAutomaticMonthlyRent_skipsShortAndOpenEnded() {
        occupancy.setStayKind(StayKind.SHORT);
        assertThat(generationService.ensureAutomaticMonthlyRent(space, occupancy, YearMonth.of(2026, 12)).write())
                .isEqualTo(SpacePaymentGenerationService.MonthlyRentWrite.NOT_CHARGEABLE);

        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis(com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis.DAILY_RATE);
        assertThat(generationService.ensureAutomaticMonthlyRent(space, occupancy, YearMonth.of(2026, 12)).write())
                .isEqualTo(SpacePaymentGenerationService.MonthlyRentWrite.NOT_CHARGEABLE);
        verify(paymentRepository, never()).save(any());
    }
}
