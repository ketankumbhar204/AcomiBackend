package com.acomi.acomi_backend.payment.application.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.acomi.acomi_backend.member.infrastructure.persistence.entity.MemberEntity;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyPricingBasis;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.payment.application.support.MonthlyRentEligibility.Evaluation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MonthlyRentEligibilityTest {

    private static final YearMonth DECEMBER = YearMonth.of(2026, 12);
    private static final LocalDate BILLING_DATE = LocalDate.of(2026, 12, 1);

    @Test
    void activeMonthlyStayIsEligible() {
        assertThat(reason(monthly(OccupancyStatus.ACTIVE, LocalDate.of(2026, 10, 1), null))).isEqualTo("GENERATE");
    }

    @Test
    void hostelCoLivingRentalAndCorporateUseTheSameMonthlyGate() {
        for (StayKind kind : new StayKind[] {StayKind.MONTHLY}) {
            OccupancyEntity occupancy = monthly(OccupancyStatus.ACTIVE, LocalDate.of(2026, 6, 1), null);
            occupancy.setStayKind(kind);
            assertThat(MonthlyRentEligibility.evaluate(occupancy, DECEMBER, BILLING_DATE).generate()).isTrue();
        }
    }

    @Test
    void movedInOnTheFirstIsEligible() {
        assertThat(reason(monthly(OccupancyStatus.ACTIVE, BILLING_DATE, LocalDateTime.of(2026, 12, 1, 8, 0))))
                .isEqualTo("GENERATE");
    }

    @Test
    void reservedTenantIsNotEligible() {
        OccupancyEntity occupancy = monthly(OccupancyStatus.RESERVED, LocalDate.of(2026, 12, 1), null);
        occupancy.setReservedAt(LocalDateTime.of(2026, 11, 20, 9, 0));
        occupancy.setActualMoveInAt(null);
        assertThat(reason(occupancy)).isEqualTo(MonthlyRentEligibility.RESERVED);
    }

    @Test
    void cancelledReservationIsNotEligible() {
        OccupancyEntity occupancy = monthly(OccupancyStatus.VACATED, LocalDate.of(2026, 12, 1), null);
        occupancy.setReservedAt(LocalDateTime.of(2026, 11, 20, 9, 0));
        occupancy.setActualMoveInAt(null);
        occupancy.setVacatedAt(LocalDateTime.of(2026, 11, 28, 9, 0));
        assertThat(reason(occupancy)).isEqualTo(MonthlyRentEligibility.NOT_MOVED_IN);
    }

    @Test
    void vacatedBeforeTheBillingDateIsNotEligible() {
        OccupancyEntity occupancy = monthly(
                OccupancyStatus.VACATED,
                LocalDate.of(2026, 8, 1),
                LocalDateTime.of(2026, 8, 1, 10, 0));
        occupancy.setVacatedAt(LocalDateTime.of(2026, 11, 30, 18, 0));
        assertThat(reason(occupancy)).isEqualTo(MonthlyRentEligibility.VACATED_BEFORE_PERIOD);
    }

    @Test
    void vacatedDuringTheMonthStaysEligibleForThatMonthOnly() {
        OccupancyEntity occupancy = monthly(
                OccupancyStatus.VACATED,
                LocalDate.of(2026, 10, 1),
                LocalDateTime.of(2026, 10, 1, 10, 0));
        occupancy.setVacatedAt(LocalDateTime.of(2026, 12, 10, 12, 0));
        assertThat(MonthlyRentEligibility.evaluate(occupancy, DECEMBER, BILLING_DATE).generate()).isTrue();
        assertThat(MonthlyRentEligibility.evaluate(occupancy, YearMonth.of(2027, 1), LocalDate.of(2027, 1, 1))
                        .reason())
                .isEqualTo(MonthlyRentEligibility.VACATED_BEFORE_PERIOD);
    }

    @Test
    void shortStayIsExcluded() {
        OccupancyEntity occupancy = monthly(OccupancyStatus.ACTIVE, LocalDate.of(2026, 12, 1), LocalDateTime.of(2026, 12, 1, 9, 0));
        occupancy.setStayKind(StayKind.SHORT);
        assertThat(reason(occupancy)).isEqualTo(MonthlyRentEligibility.SHORT_STAY);
    }

    @Test
    void openEndedDailyRateStaysOnMonthCloseEvenAfterTheMonthHasClosed() {
        OccupancyEntity occupancy = monthly(OccupancyStatus.ACTIVE, LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 9, 0));
        occupancy.setStayKind(StayKind.OPEN_ENDED);
        occupancy.setPricingBasis(OccupancyPricingBasis.DAILY_RATE);
        occupancy.setRentSnapshot(new BigDecimal("500"));
        assertThat(MonthlyRentEligibility.evaluate(occupancy, YearMonth.of(2026, 11), LocalDate.of(2026, 12, 1))
                        .reason())
                .isEqualTo(MonthlyRentEligibility.OPEN_ENDED_MONTH_CLOSE);
    }

    @Test
    void inactiveMemberIsNotEligible() {
        OccupancyEntity occupancy = monthly(OccupancyStatus.ACTIVE, LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 9, 0));
        occupancy.getMember().setActive(false);
        assertThat(reason(occupancy)).isEqualTo(MonthlyRentEligibility.INACTIVE_MEMBER);
    }

    @Test
    void futureMoveInIsNotEligibleForTheEarlierMonth() {
        OccupancyEntity occupancy = monthly(
                OccupancyStatus.ACTIVE, LocalDate.of(2027, 1, 5), LocalDateTime.of(2027, 1, 5, 9, 0));
        assertThat(MonthlyRentEligibility.evaluate(occupancy, DECEMBER, BILLING_DATE).reason())
                .isEqualTo(MonthlyRentEligibility.NOT_STARTED);
    }

    @Test
    void bookingAdvanceFreezesTheFirstPeriod() {
        OccupancyEntity occupancy = monthly(
                OccupancyStatus.ACTIVE, LocalDate.of(2026, 12, 1), LocalDateTime.of(2026, 12, 1, 9, 0));
        occupancy.setBookingAdvanceAmount(new BigDecimal("2000"));
        assertThat(reason(occupancy)).isEqualTo(MonthlyRentEligibility.RESERVATION_ADVANCE_COVERS_PERIOD);
        assertThat(MonthlyRentEligibility.evaluate(occupancy, YearMonth.of(2027, 1), LocalDate.of(2027, 1, 1))
                        .generate())
                .isTrue();
    }

    @Test
    void messPolicyDoesNotGenerateACalendarPayment() {
        assertThat(MessCalendarBillingPolicy.generatesCalendarMonthPayment()).isFalse();
        assertThat(MessCalendarBillingPolicy.EXPLANATION).contains("validity");
    }

    private static String reason(OccupancyEntity occupancy) {
        Evaluation evaluation = MonthlyRentEligibility.evaluate(occupancy, DECEMBER, BILLING_DATE);
        return evaluation.generate() ? "GENERATE" : evaluation.reason();
    }

    private static OccupancyEntity monthly(OccupancyStatus status, LocalDate moveIn, LocalDateTime actual) {
        MemberEntity member = MemberEntity.builder().fullName("Tenant").build();
        return OccupancyEntity.builder()
                .status(status)
                .stayKind(StayKind.MONTHLY)
                .member(member)
                .moveInDate(moveIn)
                .actualMoveInAt(actual != null ? actual : moveIn.atStartOfDay())
                .rentSnapshot(new BigDecimal("6000"))
                .build();
    }
}
