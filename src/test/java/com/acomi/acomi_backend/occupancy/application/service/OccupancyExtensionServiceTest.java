package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acomi.acomi_backend.accommodation.application.service.AccommodationAccessService;
import com.acomi.acomi_backend.accommodation.infrastructure.persistence.entity.BuildingEntity;
import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.api.dto.request.ExtendOccupancyRequest;
import com.acomi.acomi_backend.occupancy.application.support.StayAvailabilityRules;
import com.acomi.acomi_backend.occupancy.application.support.StayExtensionRules;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyHistoryEvent;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyHistoryEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyHistoryRepository;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentTimelineService;
import com.acomi.acomi_backend.payment.application.support.PaymentDueStatusCalculator;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentCategory;
import com.acomi.acomi_backend.payment.domain.model.SpacePaymentType;
import com.acomi.acomi_backend.payment.infrastructure.persistence.entity.SpacePaymentEntity;
import com.acomi.acomi_backend.payment.infrastructure.persistence.repository.SpacePaymentRepository;
import com.acomi.acomi_backend.space.domain.model.SpaceType;
import com.acomi.acomi_backend.space.infrastructure.persistence.entity.SpaceEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OccupancyExtensionServiceTest {

    @Mock
    private OccupancyRepository occupancyRepository;

    @Mock
    private OccupancyHistoryRepository occupancyHistoryRepository;

    @Mock
    private OccupancyAccessService occupancyAccessService;

    @Mock
    private AccommodationAccessService accommodationAccessService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpacePaymentRepository spacePaymentRepository;

    @Mock
    private SpacePaymentTimelineService spacePaymentTimelineService;

    @Mock
    private StayRangeGuard stayRangeGuard;

    @InjectMocks
    private OccupancyExtensionService extensionService;

    private UUID spaceId;
    private UUID occupancyId;
    private UUID callerId;
    private SpaceEntity space;
    private OccupancyEntity occupancy;
    private LocalDate businessDate;

    @BeforeEach
    void setUp() {
        spaceId = UUID.randomUUID();
        occupancyId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        space = SpaceEntity.builder().type(SpaceType.PG).timezone("Asia/Kolkata").build();
        space.setId(spaceId);
        businessDate = PaymentDueStatusCalculator.businessDate(space);

        MemberEntity member = MemberEntity.builder().fullName("Asha").space(space).build();
        member.setId(UUID.randomUUID());
        UserEntity actor = UserEntity.builder().build();
        actor.setId(callerId);
        BuildingEntity building = BuildingEntity.builder().space(space).name("Block A").code("A").build();
        building.setId(UUID.randomUUID());

        LocalDate stayStart = businessDate.plusMonths(1).withDayOfMonth(10);
        occupancy = OccupancyEntity.builder()
                .space(space)
                .member(member)
                .targetType(AllocationTargetType.BED)
                .building(building)
                .status(OccupancyStatus.ACTIVE)
                .stayKind(StayKind.SHORT)
                .moveInDate(stayStart)
                .expectedExitDate(stayStart.plusDays(2))
                .depositSnapshot(new BigDecimal("2000"))
                .build();
        occupancy.setId(occupancyId);

        lenient().when(accommodationAccessService.loadAccommodationSpace(spaceId)).thenReturn(space);
        lenient().when(userRepository.findById(callerId)).thenReturn(Optional.of(actor));
        lenient().when(occupancyRepository.findByIdAndSpaceIdForUpdate(occupancyId, spaceId))
                .thenReturn(Optional.of(occupancy));
        lenient().when(occupancyHistoryRepository.findByOccupancy_IdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(spacePaymentRepository.save(any())).thenAnswer(invocation -> {
            SpacePaymentEntity saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void oneNovemberThroughEightNovember_isSevenNights() {
        assertThat(StayExtensionRules.nights(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)))
                .isEqualTo(7);
    }

    @Test
    void shortExtension_createsOneExtraForTheAddedHalfOpenInterval() {
        LocalDate currentEnd = occupancy.getExpectedExitDate();
        LocalDate newEnd = currentEnd.plusDays(2);
        ExtendOccupancyRequest request = request(newEnd, new BigDecimal("1600"));

        OccupancyExtensionService.Result result = extensionService.extend(spaceId, occupancyId, callerId, request);

        assertThat(result.chargeId()).isNotNull();
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(newEnd);
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getVacatedAt()).isNull();
        assertThat(occupancy.getDepositSnapshot()).isEqualByComparingTo("2000");

        ArgumentCaptor<SpacePaymentEntity> charge = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(spacePaymentRepository).save(charge.capture());
        SpacePaymentEntity saved = charge.getValue();
        assertThat(saved.getPaymentType()).isEqualTo(SpacePaymentType.RENT);
        assertThat(saved.getPaymentCategory()).isEqualTo(SpacePaymentCategory.EXTRA);
        assertThat(saved.getAmount()).isEqualByComparingTo("1600");
        assertThat(saved.getBillingPeriodStart()).isEqualTo(currentEnd);
        assertThat(saved.getBillingPeriodEnd()).isEqualTo(newEnd);
        assertThat(saved.getBillableDays()).isEqualTo(2);
        assertThat(result.chargeId()).isEqualTo(saved.getId());

        ArgumentCaptor<OccupancyHistoryEntity> history = ArgumentCaptor.forClass(OccupancyHistoryEntity.class);
        verify(occupancyHistoryRepository).save(history.capture());
        assertThat(history.getValue().getEventType()).isEqualTo(OccupancyHistoryEvent.EXTENDED);
        assertThat(history.getValue().getExtensionChargeId()).isEqualTo(saved.getId());
    }

    @Test
    void secondShortExtension_chargesOnlyTheNewInterval() {
        LocalDate currentEnd = occupancy.getExpectedExitDate().plusDays(2);
        occupancy.setExpectedExitDate(currentEnd);
        LocalDate newEnd = currentEnd.plusDays(2);

        OccupancyExtensionService.Result result =
                extensionService.extend(spaceId, occupancyId, callerId, request(newEnd, new BigDecimal("800")));

        ArgumentCaptor<SpacePaymentEntity> charge = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(spacePaymentRepository).save(charge.capture());
        assertThat(charge.getValue().getBillingPeriodStart()).isEqualTo(currentEnd);
        assertThat(charge.getValue().getBillingPeriodEnd()).isEqualTo(newEnd);
        assertThat(charge.getValue().getAmount()).isEqualByComparingTo("800");
        assertThat(result.chargeId()).isEqualTo(charge.getValue().getId());
    }

    @Test
    void freeShortExtension_recordsHistoryWithoutAPaymentRow() {
        LocalDate newEnd = occupancy.getExpectedExitDate().plusDays(1);

        OccupancyExtensionService.Result result =
                extensionService.extend(spaceId, occupancyId, callerId, request(newEnd, BigDecimal.ZERO));

        assertThat(result.chargeId()).isNull();
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(newEnd);
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyHistoryRepository).save(any());
    }

    @Test
    void absentAmount_isAlsoFree() {
        LocalDate newEnd = occupancy.getExpectedExitDate().plusDays(1);
        ExtendOccupancyRequest request = request(newEnd, null);

        OccupancyExtensionService.Result result = extensionService.extend(spaceId, occupancyId, callerId, request);

        assertThat(result.chargeId()).isNull();
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void monthlyExtension_updatesTheDateWithoutAnExtraCharge() {
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("10000"));
        LocalDate currentEnd = LocalDate.of(2027, 1, 31);
        occupancy.setExpectedExitDate(currentEnd);
        occupancy.setMoveInDate(LocalDate.of(2026, 1, 1));

        OccupancyExtensionService.Result result =
                extensionService.extend(spaceId, occupancyId, callerId, request(LocalDate.of(2027, 2, 3), null));

        assertThat(result.chargeId()).isNull();
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(LocalDate.of(2027, 2, 3));
        assertThat(occupancy.getRentSnapshot()).isEqualByComparingTo("10000");
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyHistoryRepository).save(any());
    }

    @Test
    void monthlyExtension_rejectsAnAdditionalAmount() {
        occupancy.setStayKind(null);
        LocalDate currentEnd = occupancy.getExpectedExitDate();

        assertThatThrownBy(() -> extensionService.extend(
                spaceId, occupancyId, callerId, request(currentEnd.plusDays(1), new BigDecimal("500"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.AMOUNT_NOT_FOR_MONTHLY);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void shortExtension_rejectsWhenTheAddedNightPutsTheStayInASecondMonth() {
        LocalDate currentEnd = businessDate.plusMonths(1).withDayOfMonth(1);
        occupancy.setMoveInDate(currentEnd.minusDays(1));
        occupancy.setExpectedExitDate(currentEnd);
        LocalDate newEnd = currentEnd.plusDays(1);

        assertThat(StayExtensionRules.crossesCalendarMonth(currentEnd, newEnd)).isFalse();
        assertThat(StayExtensionRules.crossesCalendarMonth(occupancy.getMoveInDate(), newEnd)).isTrue();
        assertThatThrownBy(() -> extensionService.extend(
                spaceId, occupancyId, callerId, request(newEnd, new BigDecimal("500"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.CROSSES_MONTH);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void openEndedExtension_rejectsAnAdditionalAmount() {
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        LocalDate currentEnd = occupancy.getExpectedExitDate();

        assertThatThrownBy(() -> extensionService.extend(
                spaceId, occupancyId, callerId, request(currentEnd.plusDays(1), new BigDecimal("500"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.AMOUNT_NOT_FOR_MONTHLY);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void shortExtension_rejectsACrossMonthInterval() {
        LocalDate currentEnd = businessDate.withDayOfMonth(1).plusMonths(1).withDayOfMonth(
                businessDate.withDayOfMonth(1).plusMonths(1).lengthOfMonth());
        occupancy.setExpectedExitDate(currentEnd);
        occupancy.setMoveInDate(currentEnd.minusDays(3));
        LocalDate newEnd = currentEnd.plusDays(2);

        assertThatThrownBy(() -> extensionService.extend(
                spaceId, occupancyId, callerId, request(newEnd, new BigDecimal("900"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.CROSSES_MONTH);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void checkoutOnTheFirstOfTheNextMonth_doesNotCross() {
        LocalDate currentEnd = businessDate.withDayOfMonth(1).plusMonths(1).withDayOfMonth(
                businessDate.withDayOfMonth(1).plusMonths(1).lengthOfMonth());
        occupancy.setExpectedExitDate(currentEnd);
        occupancy.setMoveInDate(currentEnd.minusDays(3));
        LocalDate newEnd = currentEnd.plusDays(1);

        extensionService.extend(spaceId, occupancyId, callerId, request(newEnd, new BigDecimal("400")));

        ArgumentCaptor<SpacePaymentEntity> charge = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(spacePaymentRepository).save(charge.capture());
        assertThat(charge.getValue().getBillingPeriodStart()).isEqualTo(currentEnd);
        assertThat(charge.getValue().getBillingPeriodEnd()).isEqualTo(newEnd);
        assertThat(charge.getValue().getBillableDays()).isEqualTo(1);
    }

    @Test
    void overdueShortStay_acceptsAnEndOnTheBusinessDate() {
        LocalDate moveIn = businessDate.withDayOfMonth(1);
        LocalDate currentEnd = moveIn.plusDays(1);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                currentEnd.isBefore(businessDate)
                        && !StayExtensionRules.crossesCalendarMonth(moveIn, businessDate));
        occupancy.setMoveInDate(moveIn);
        occupancy.setExpectedExitDate(currentEnd);

        extensionService.extend(spaceId, occupancyId, callerId, request(businessDate, new BigDecimal("700")));

        assertThat(occupancy.getExpectedExitDate()).isEqualTo(businessDate);
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getVacatedAt()).isNull();
        ArgumentCaptor<SpacePaymentEntity> charge = ArgumentCaptor.forClass(SpacePaymentEntity.class);
        verify(spacePaymentRepository).save(charge.capture());
        assertThat(charge.getValue().getBillingPeriodStart()).isEqualTo(currentEnd);
        assertThat(charge.getValue().getBillingPeriodEnd()).isEqualTo(businessDate);
    }

    @Test
    void overdueMonthlyStay_acceptsTheBusinessDateWithoutACharge() {
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("10000"));
        LocalDate currentEnd = businessDate.minusDays(2);
        occupancy.setMoveInDate(currentEnd.minusMonths(1));
        occupancy.setExpectedExitDate(currentEnd);

        OccupancyExtensionService.Result result =
                extensionService.extend(spaceId, occupancyId, callerId, request(businessDate, null));

        assertThat(result.chargeId()).isNull();
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(businessDate);
        assertThat(occupancy.getVacatedAt()).isNull();
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void overdueStay_rejectsAnEndStillBeforeTheBusinessDate() {
        LocalDate currentEnd = businessDate.minusDays(3);
        occupancy.setMoveInDate(currentEnd.minusDays(4));
        occupancy.setExpectedExitDate(currentEnd);

        assertThatThrownBy(() -> extensionService.extend(
                spaceId, occupancyId, callerId, request(businessDate.minusDays(1), new BigDecimal("100"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.ON_OR_AFTER_BUSINESS_DATE);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getVacatedAt()).isNull();
        verify(occupancyRepository, never()).save(any());
        verify(occupancyHistoryRepository, never()).save(any());
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void vacatedStay_isRejectedWithoutACharge() {
        occupancy.setStatus(OccupancyStatus.VACATED);

        assertThatThrownBy(() -> extensionService.extend(
                spaceId, occupancyId, callerId, request(occupancy.getExpectedExitDate().plusDays(1), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.ACTIVE_ONLY);
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void sameIdempotencyKey_returnsTheFirstChargeAndDoesNotInsertAgain() {
        UUID chargeId = UUID.randomUUID();
        OccupancyHistoryEntity existing = OccupancyHistoryEntity.builder()
                .eventType(OccupancyHistoryEvent.EXTENDED)
                .extensionChargeId(chargeId)
                .idempotencyKey("same-key")
                .build();
        when(occupancyHistoryRepository.findByOccupancy_IdAndIdempotencyKey(occupancyId, "same-key"))
                .thenReturn(Optional.of(existing));
        LocalDate currentEnd = occupancy.getExpectedExitDate();
        ExtendOccupancyRequest request = request(currentEnd.plusDays(3), new BigDecimal("1600"));
        request.setIdempotencyKey("same-key");

        OccupancyExtensionService.Result result = extensionService.extend(spaceId, occupancyId, callerId, request);

        assertThat(result.chargeId()).isEqualTo(chargeId);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyRepository, never()).save(any());
        verify(occupancyHistoryRepository, never()).save(any());
        verify(stayRangeGuard, never())
                .assertAvailable(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void extend_whenRangeOverlaps_doesNotSaveTheNewEndOrCharge() {
        LocalDate currentEnd = occupancy.getExpectedExitDate();
        doThrow(new BusinessException("STAY_OVERLAP", StayAvailabilityRules.OVERLAP))
                .when(stayRangeGuard)
                .assertAvailable(
                        any(),
                        nullable(UUID.class),
                        nullable(UUID.class),
                        nullable(UUID.class),
                        any(),
                        any(),
                        any());

        assertThatThrownBy(() -> extensionService.extend(
                        spaceId, occupancyId, callerId, request(currentEnd.plusDays(2), new BigDecimal("1600"))))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayAvailabilityRules.OVERLAP);
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        assertThat(occupancy.getStatus()).isEqualTo(OccupancyStatus.ACTIVE);
        assertThat(occupancy.getVacatedAt()).isNull();
        verify(occupancyRepository, never()).save(any());
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void rentChange_storesThePreviousPriceFromTheSelectedMonth() {
        occupancy.setStayKind(StayKind.MONTHLY);
        occupancy.setRentSnapshot(new BigDecimal("10000"));
        LocalDate currentEnd = businessDate.plusMonths(1).withDayOfMonth(1);
        occupancy.setMoveInDate(businessDate.minusMonths(2).withDayOfMonth(1));
        occupancy.setExpectedExitDate(currentEnd);
        ExtendOccupancyRequest request = request(currentEnd.plusMonths(1), null);
        request.setRentSnapshot(new BigDecimal("11000"));
        request.setRentEffectiveMonth(currentEnd);

        extensionService.extend(spaceId, occupancyId, callerId, request);

        assertThat(occupancy.getPriorRentSnapshot()).isEqualByComparingTo("10000");
        assertThat(occupancy.getRentSnapshot()).isEqualByComparingTo("11000");
        assertThat(occupancy.getRentEffectiveMonth()).isEqualTo(currentEnd.withDayOfMonth(1));
        verify(spacePaymentRepository, never()).save(any());
    }

    @Test
    void rentChange_requiresTheEffectiveMonth() {
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setRentSnapshot(new BigDecimal("10000"));
        ExtendOccupancyRequest request = request(occupancy.getExpectedExitDate().plusMonths(1), null);
        request.setRentSnapshot(new BigDecimal("11000"));

        assertThatThrownBy(() -> extensionService.extend(spaceId, occupancyId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.RENT_FIELDS);
        assertThat(occupancy.getRentSnapshot()).isEqualByComparingTo("10000");
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void shortStay_rejectsAMonthlyRentChange() {
        ExtendOccupancyRequest request = request(occupancy.getExpectedExitDate().plusDays(1), null);
        request.setRentSnapshot(new BigDecimal("11000"));
        request.setRentEffectiveMonth(occupancy.getExpectedExitDate());

        assertThatThrownBy(() -> extensionService.extend(spaceId, occupancyId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.RENT_NOT_FOR_SHORT);
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void openEndedRentOnly_revisesDailyRateWithoutChangingPlannedEnd() {
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis("DAILY_RATE");
        occupancy.setRentSnapshot(new BigDecimal("1000"));
        LocalDate currentEnd = occupancy.getExpectedExitDate();
        LocalDate effective = businessDate.plusDays(1);

        ExtendOccupancyRequest request = new ExtendOccupancyRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setRentSnapshot(new BigDecimal("1200"));
        request.setRentEffectiveMonth(effective);
        request.setReason("Rent revision");

        OccupancyExtensionService.Result result =
                extensionService.extend(spaceId, occupancyId, callerId, request);

        assertThat(result.chargeId()).isNull();
        assertThat(occupancy.getExpectedExitDate()).isEqualTo(currentEnd);
        assertThat(occupancy.getStayKind()).isEqualTo(StayKind.OPEN_ENDED);
        assertThat(occupancy.getPriorRentSnapshot()).isEqualByComparingTo("1000");
        assertThat(occupancy.getRentSnapshot()).isEqualByComparingTo("1200");
        assertThat(occupancy.getRentEffectiveMonth()).isEqualTo(effective);
        assertThat(occupancy.getDepositSnapshot()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(spacePaymentRepository, never()).save(any());
        verify(occupancyRepository).save(occupancy);
    }

    @Test
    void openEndedRentOnly_rejectsUnchangedRate() {
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setRentSnapshot(new BigDecimal("1000"));
        ExtendOccupancyRequest request = new ExtendOccupancyRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setRentSnapshot(new BigDecimal("1000"));
        request.setRentEffectiveMonth(businessDate.plusDays(1));

        assertThatThrownBy(() -> extensionService.extend(spaceId, occupancyId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.RENT_UNCHANGED);
        verify(occupancyRepository, never()).save(any());
    }

    @Test
    void openEndedRentOnly_rejectsEffectiveBeforeBusinessDate() {
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setRentSnapshot(new BigDecimal("1000"));
        ExtendOccupancyRequest request = new ExtendOccupancyRequest();
        request.setIdempotencyKey(UUID.randomUUID().toString());
        request.setRentSnapshot(new BigDecimal("1200"));
        request.setRentEffectiveMonth(businessDate.minusDays(1));

        assertThatThrownBy(() -> extensionService.extend(spaceId, occupancyId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage(StayExtensionRules.RENT_EFFECTIVE_BEFORE_BUSINESS);
        verify(occupancyRepository, never()).save(any());
    }

    private ExtendOccupancyRequest request(LocalDate newEnd, BigDecimal additionalAmount) {
        ExtendOccupancyRequest request = new ExtendOccupancyRequest();
        request.setExpectedExitDate(newEnd);
        request.setAdditionalAmount(additionalAmount);
        request.setIdempotencyKey(UUID.randomUUID().toString());
        return request;
    }
}
