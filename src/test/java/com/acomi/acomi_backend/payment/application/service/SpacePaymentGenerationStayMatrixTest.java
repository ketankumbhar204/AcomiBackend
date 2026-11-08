package com.acomi.acomi_backend.payment.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.dashboard.application.support.PayPerMealBillingCalculator;
import com.acomi.acomi_backend.meal.application.support.MealBillingResolver;
import com.acomi.acomi_backend.meal.infrastructure.persistence.repository.MealParticipationRepository;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.member.infrastructure.persistence.repository.MemberRepository;
import com.acomi.acomi_backend.occupancy.application.service.OccupancyTargetLabelBuilder;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stay Management payment matrix: SpaceType × StayKind × activation associations.
 * MESS is asserted as non-applicable (no lodging payment generation).
 */
@ExtendWith(MockitoExtension.class)
class SpacePaymentGenerationStayMatrixTest {

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
    private UUID occupancyId;
    private UUID ownerId;
    private SpaceEntity space;
    private MemberEntity member;
    private OccupancyEntity occupancy;

    static Stream<Arguments> accommodationSpaceTypes() {
        return Stream.of(
                Arguments.of(SpaceType.PG),
                Arguments.of(SpaceType.HOSTEL),
                Arguments.of(SpaceType.CO_LIVING),
                Arguments.of(SpaceType.RENTAL),
                Arguments.of(SpaceType.CORPORATE_STAY));
    }

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        occupancyId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        space = SpaceEntity.builder().type(SpaceType.PG).billingDueDay(1).taxEnabled(false).build();
        space.setId(spaceId);

        member = MemberEntity.builder().fullName("Test Member").space(space).build();
        member.setId(memberId);

        occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .status(OccupancyStatus.ACTIVE)
                .moveInDate(LocalDate.of(2026, 11, 1))
                .actualMoveInAt(LocalDateTime.of(2026, 11, 1, 10, 0))
                .rentSnapshot(new BigDecimal("6000"))
                .depositSnapshot(new BigDecimal("3000"))
                .foodEnabled(false)
                .foodIncludedInRent(false)
                .build();
        occupancy.setId(occupancyId);

        lenient().when(occupancyTargetLabelBuilder.build(any())).thenReturn("Target");
        lenient()
                .when(paymentRepository.findBySpaceIdAndMemberIdAndMonthAndPaymentTypeAndPaymentCategory(
                        any(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient()
                .when(paymentRepository
                        .findByOccupancy_IdAndPaymentTypeAndPaymentCategoryAndBillingPeriodStartAndBillingPeriodEnd(
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

    private void bindSpaceType(SpaceType type) {
        space = SpaceEntity.builder().type(type).billingDueDay(1).taxEnabled(false).build();
        space.setId(spaceId);
        member = MemberEntity.builder().fullName("Test Member").space(space).build();
        member.setId(memberId);
        occupancy.setSpace(space);
        occupancy.setMember(member);
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void monthlyAllocate_createsRentAndDeposit_withMemberSpaceOccupancy(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setPricingBasis(null);
        occupancy.setRentSnapshot(new BigDecimal("6000"));
        occupancy.setDepositSnapshot(new BigDecimal("3000"));

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        List<SpacePaymentEntity> saved = captor.getAllValues();

        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.MONTHLY);
                    assertThat(payment.getAmount()).isEqualByComparingTo("6000");
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                    assertThat(payment.getMember().getId()).isEqualTo(memberId);
                    assertThat(payment.getSpace().getId()).isEqualTo(spaceId);
                    assertThat(payment.getOccupancy().getId()).isEqualTo(occupancyId);
                    assertThat(payment.getMember().getId()).isNotEqualTo(callerId);
                    assertThat(payment.getMember().getId()).isNotEqualTo(ownerId);
                });
        assertThat(saved)
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.SECURITY);
                    assertThat(payment.getAmount()).isEqualByComparingTo("3000");
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                    assertThat(payment.getMember().getId()).isEqualTo(memberId);
                    assertThat(payment.getOccupancy().getId()).isEqualTo(occupancyId);
                });
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void shortStayAllocate_createsExtraAgreedTotal_notMonthlyRent(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setPricingBasis(OccupancyPricingBasis.FIXED_TOTAL);
        occupancy.setRentSnapshot(null);
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        occupancy.setDepositSnapshot(new BigDecimal("2000"));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 8));

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneSatisfy(p -> assertThat(p.getPaymentCategory()).isEqualTo(SpacePaymentCategory.MONTHLY));
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.EXTRA);
                    assertThat(payment.getAmount()).isEqualByComparingTo("5600");
                    assertThat(payment.getMember().getId()).isEqualTo(memberId);
                    assertThat(payment.getSpace().getId()).isEqualTo(spaceId);
                    assertThat(payment.getOccupancy().getId()).isEqualTo(occupancyId);
                    assertThat(payment.getPaymentStatus()).isEqualTo(SpacePaymentStatus.PENDING);
                    assertThat(payment.getBillableDays()).isEqualTo(7);
                });
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void untilILeaveDailyRate_activationCreatesDepositOnly_noOpenMonthRent(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
        // Daily rent ₹250 — open November must not generate a payable RENT row at activation.
        occupancy.setRentSnapshot(new BigDecimal("250"));
        occupancy.setDepositSnapshot(new BigDecimal("1000"));

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneSatisfy(payment -> assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT));
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT);
                    assertThat(payment.getAmount()).isEqualByComparingTo("1000");
                    assertThat(payment.getMember().getId()).isEqualTo(memberId);
                    assertThat(payment.getOccupancy().getId()).isEqualTo(occupancyId);
                });
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void untilILeaveDailyRate_vacateSettlesActualOccupiedDays(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
        occupancy.setRentSnapshot(new BigDecimal("1000"));
        occupancy.setDepositSnapshot(BigDecimal.ZERO);
        occupancy.setMoveInDate(LocalDate.of(2026, 11, 5));
        occupancy.setActualMoveInAt(LocalDateTime.of(2026, 11, 5, 10, 0));
        occupancy.setStatus(OccupancyStatus.VACATED);
        occupancy.setVacatedAt(LocalDateTime.of(2026, 11, 12, 12, 0));

        generationService.settleOpenEndedRentOnVacate(occupancy);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getPaymentCategory()).isEqualTo(SpacePaymentCategory.MONTHLY);
                    // Nov 5–12 inclusive = 8 days × ₹1000
                    assertThat(payment.getAmount()).isEqualByComparingTo("8000");
                    assertThat(payment.getBillableDays()).isEqualTo(8);
                    assertThat(payment.getOccupancy().getId()).isEqualTo(occupancyId);
                });
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void legacyOpenEnded_nullPricingBasis_usesMonthlyRentSnapshot(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis(null);
        occupancy.setRentSnapshot(new BigDecimal("9000"));
        occupancy.setDepositSnapshot(BigDecimal.ZERO);

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(payment -> {
                    assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
                    assertThat(payment.getAmount()).isEqualByComparingTo("9000");
                });
        assertThat(captor.getAllValues())
                .noneSatisfy(payment -> assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT));
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void zeroDeposit_doesNotCreateSecurityPayment(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setDepositSnapshot(BigDecimal.ZERO);
        occupancy.setRentSnapshot(new BigDecimal("5000"));

        generationService.createActivationPayments(occupancy, callerId);

        ArgumentCaptor<SpacePaymentEntity> captor = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .noneSatisfy(payment -> assertThat(payment.getPaymentType()).isEqualTo(SpacePaymentType.DEPOSIT));
    }

    @ParameterizedTest
    @MethodSource("accommodationSpaceTypes")
    void shortStay_secondActivation_isIdempotent(SpaceType spaceType) {
        bindSpaceType(spaceType);
        occupancy.setStayKind(StayKind.SHORT);
        occupancy.setAgreedTotal(new BigDecimal("5600"));
        occupancy.setDepositSnapshot(new BigDecimal("2000"));
        occupancy.setExpectedExitDate(LocalDate.of(2026, 11, 8));
        SpacePaymentEntity existingExtra = SpacePaymentEntity.builder()
                .occupancy(occupancy)
                .paymentType(SpacePaymentType.RENT)
                .paymentCategory(SpacePaymentCategory.EXTRA)
                .amount(new BigDecimal("5600"))
                .billingPeriodStart(LocalDate.of(2026, 11, 1))
                .billingPeriodEnd(LocalDate.of(2026, 11, 8))
                .build();
        // PAID deposit is frozen (same rule as SpacePaymentGenerationServiceTest idempotency case).
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
                                occupancyId,
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

    @ParameterizedTest
    @EnumSource(value = SpaceType.class, names = "MESS")
    void mess_doesNotGenerateLodgingActivationPayments(SpaceType mess) {
        bindSpaceType(mess);
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("1000"));
        occupancy.setDepositSnapshot(new BigDecimal("500"));

        generationService.createActivationPayments(occupancy, callerId);

        verify(paymentRepository, never()).save(any());
    }
}
