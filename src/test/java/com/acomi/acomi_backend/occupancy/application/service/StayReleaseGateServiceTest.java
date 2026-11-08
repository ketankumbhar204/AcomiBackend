package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.application.service.AccommodationStatusSyncService;
import com.acomi.acomi_backend.config.security.UserPrincipal;
import com.acomi.acomi_backend.dashboard.application.service.SpaceDashboardSummaryService;
import com.acomi.acomi_backend.occupancy.api.controller.OccupancyController;
import com.acomi.acomi_backend.occupancy.api.dto.request.AllocateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.ExtendOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.ReserveOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.TransferOccupancyRequest;
import com.acomi.acomi_backend.occupancy.api.dto.request.VacateOccupancyRequest;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.StayKind;
import com.acomi.acomi_backend.occupancy.domain.model.TransferRentPolicy;
import com.acomi.acomi_backend.payment.api.dto.request.MarkPaymentReceivedRequest;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentGenerationService;
import com.acomi.acomi_backend.payment.application.service.SpacePaymentService;
import com.acomi.acomi_backend.user.infrastructure.persistence.entity.UserEntity;
import com.acomi.acomi_backend.user.infrastructure.persistence.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Service-posted ledger and extension races. The database name must match
 * {@code acomi_release_gate_[0-9a-f]{12}}. This class never opens {@code acomi_db}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIf("postgresAvailable")
@TestMethodOrder(OrderAnnotation.class)
class StayReleaseGateServiceTest {

    private static final AtomicInteger CODES = new AtomicInteger(400);

    private static ReleaseGateIsolatedDatabase database;

    @Autowired
    private DataSource dataSource;
    @Autowired
    private OccupancyService occupancyService;
    @Autowired
    private OccupancyExtensionService occupancyExtensionService;
    @Autowired
    private OccupancyController occupancyController;
    @Autowired
    private SpacePaymentGenerationService paymentGenerationService;
    @Autowired
    private SpacePaymentService spacePaymentService;
    @Autowired
    private SpaceDashboardSummaryService dashboardSummaryService;
    @Autowired
    private AccommodationStatusSyncService accommodationStatusSyncService;
    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void disposableDatabase(DynamicPropertyRegistry registry) throws Exception {
        database = ReleaseGateIsolatedDatabase.open();
        if (database.url.contains("/acomi_db") || "acomi_db".equals(database.name)) {
            throw new IllegalStateException("Refusing shared database " + database.name);
        }
        System.out.println("SERVICE_LEDGER_DB=" + database.name);
        registry.add("spring.datasource.url", () -> database.url);
        registry.add("spring.datasource.username", () -> ReleaseGateIsolatedDatabase.USER);
        registry.add("spring.datasource.password", () -> ReleaseGateIsolatedDatabase.PASSWORD);
        registry.add("DB_NAME", () -> database.name);
        registry.add("acomi.reminders.scheduler.enabled", () -> "false");
        registry.add("spring.task.scheduling.enabled", () -> "false");
    }

    @AfterAll
    static void dropDisposableDatabase() throws Exception {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    static boolean postgresAvailable() {
        return ReleaseGateIsolatedDatabase.postgresAvailable();
    }

    @BeforeEach
    void requireDisposableCatalog() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if ("acomi_db".equals(catalog) || !database.name.equals(catalog)) {
                throw new IllegalStateException("Refusing catalog " + catalog);
            }
        }
        mockMvc = MockMvcBuilders.standaloneSetup(occupancyController).build();
    }

    @Test
    @Order(1)
    void legacyAllocateBodyPersistsMonthlyStayAndRent() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        LocalDate checkout = LocalDate.now().plusDays(20);
        String body = """
                {"memberId":"%s","targetType":"BED","bedId":"%s","expectedCheckoutDate":"%s","rentSnapshot":10000,"depositSnapshot":500}
                """.formatted(memberId, graph.newBed(), checkout);
        asOwner(graph.ownerId);
        try {
            mockMvc.perform(post("/api/v1/spaces/{spaceId}/occupancies", graph.spaceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        } finally {
            SecurityContextHolder.clearContext();
        }
        UUID occupancyId = soleOccupancy(memberId);
        assertThat(stayKind(occupancyId)).isNull();
        assertThat(dateColumn(occupancyId, "expected_checkout_date")).isEqualTo(checkout);
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(checkout);
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
    }

    @Test
    @Order(2)
    void shortStayServiceWritesExtraAndDepositWithoutMonthlyRent() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        LocalDate checkout = sameMonthCheckout();
        AllocateOccupancyRequest request = bedRequest(memberId, graph.newBed());
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setDepositSnapshot(new BigDecimal("2000"));
        request.setExpectedCheckoutDate(checkout);
        var response = occupancyService.allocate(graph.spaceId, graph.ownerId, request);
        UUID occupancyId = response.getOccupancyId();
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "RENT", "EXTRA")).isEqualByComparingTo("5600.00");
        assertThat(paymentStatus(occupancyId, "RENT", "EXTRA")).isEqualTo("PENDING");
        assertThat(paymentPeriodEnd(occupancyId, "RENT", "EXTRA")).isEqualTo(checkout);
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("2000.00");
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isZero();
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now());
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isZero();
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isEqualTo(1);
    }

    @Test
    @Order(3)
    void shortStayExtensionThatLeavesTheMonthWritesNoExtra() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        LocalDate checkout = sameMonthCheckout();
        AllocateOccupancyRequest request = bedRequest(memberId, graph.newBed());
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setDepositSnapshot(new BigDecimal("2000"));
        request.setExpectedCheckoutDate(checkout);
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
        ExtendOccupancyRequest extend = new ExtendOccupancyRequest();
        extend.setExpectedExitDate(checkout.plusMonths(1));
        extend.setAdditionalAmount(new BigDecimal("1600"));
        extend.setIdempotencyKey("cross-month-" + occupancyId);
        assertThatThrownBy(() -> occupancyExtensionService.extend(graph.spaceId, occupancyId, graph.ownerId, extend))
                .isInstanceOf(BusinessException.class);
        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(checkout);
        assertThat(dateColumn(occupancyId, "expected_checkout_date")).isEqualTo(checkout);
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "RENT", "EXTRA")).isEqualByComparingTo("5600.00");
        assertThat(paymentStatus(occupancyId, "RENT", "EXTRA")).isEqualTo("PENDING");
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("2000.00");
        assertThat(paymentStatus(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo("PENDING");
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isZero();
        assertThat(historyCount(occupancyId, extend.getIdempotencyKey())).isZero();
    }

    @Test
    @Order(4)
    void vacateLeavesAPaidShortStayChargeUnchanged() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        AllocateOccupancyRequest request = bedRequest(memberId, graph.newBed());
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setDepositSnapshot(new BigDecimal("2000"));
        request.setExpectedCheckoutDate(sameMonthCheckout());
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
        UUID paymentId = paymentId(occupancyId, "RENT", "EXTRA");
        MarkPaymentReceivedRequest received = new MarkPaymentReceivedRequest();
        received.setRemarks("received");
        spacePaymentService.markReceived(graph.spaceId, paymentId, graph.ownerId, received);
        occupancyService.vacate(graph.spaceId, occupancyId, graph.ownerId, new VacateOccupancyRequest());
        assertThat(paymentStatus(occupancyId, "RENT", "EXTRA")).isEqualTo("PAID");
        assertThat(paymentAmount(occupancyId, "RENT", "EXTRA")).isEqualByComparingTo("5600.00");
        assertThat(occupancyStatus(occupancyId)).isEqualTo("VACATED");
    }

    @Test
    @Order(5)
    void monthlyExtensionWritesNoExtraAndReplayKeepsOneHistoryRow() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        UUID occupancyId = monthlyStay(graph, memberId, graph.newBed(), new BigDecimal("8000"));
        LocalDate firstEnd = LocalDate.now().plusDays(40);
        ExtendOccupancyRequest extend = extendRequest(firstEnd, "monthly-extend-" + occupancyId);
        occupancyExtensionService.extend(graph.spaceId, occupancyId, graph.ownerId, extend);
        occupancyExtensionService.extend(graph.spaceId, occupancyId, graph.ownerId, extend);
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
        assertThat(historyCount(occupancyId, extend.getIdempotencyKey())).isEqualTo(1);
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(firstEnd);
    }

    @Test
    @Order(6)
    void rentChangeLeavesThePaidCurrentMonthRowUntouched() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        UUID occupancyId = monthlyStay(graph, memberId, graph.newBed(), new BigDecimal("10000"));
        UUID paymentId = paymentId(occupancyId, "RENT", "MONTHLY");
        BigDecimal paidAmount = paymentAmount(occupancyId, "RENT", "MONTHLY");
        MarkPaymentReceivedRequest received = new MarkPaymentReceivedRequest();
        received.setRemarks("october paid");
        spacePaymentService.markReceived(graph.spaceId, paymentId, graph.ownerId, received);
        LocalDate nextMonth = YearMonth.now().plusMonths(1).atDay(1);
        ExtendOccupancyRequest extend = extendRequest(nextMonth.plusDays(20), "rent-change-" + occupancyId);
        extend.setRentSnapshot(new BigDecimal("11000"));
        extend.setRentEffectiveMonth(nextMonth);
        occupancyExtensionService.extend(graph.spaceId, occupancyId, graph.ownerId, extend);
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now());
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now().plusMonths(1));
        assertThat(paymentStatusForMonth(occupancyId, "RENT", "MONTHLY", YearMonth.now().toString()))
                .isEqualTo("PAID");
        assertThat(paymentAmountForMonth(occupancyId, "RENT", "MONTHLY", YearMonth.now().toString()))
                .isEqualByComparingTo(paidAmount);
        assertThat(paymentAmountForMonth(occupancyId, "RENT", "MONTHLY", YearMonth.now().plusMonths(1).toString()))
                .isEqualByComparingTo("11000.00");
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
    }

    @Test
    @Order(7)
    void messSyncCreatesNoRent() throws Exception {
        Graph graph = graph("MESS", false, false);
        member(graph.spaceId);
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now());
        assertThat(spacePaymentCount(graph.spaceId, "RENT")).isZero();
    }

    @Test
    @Order(8)
    void dashboardCountsOccupiedAndReservedBedsSeparately() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID staying = member(graph.spaceId);
        monthlyStay(graph, staying, graph.newBed(), new BigDecimal("9000"));
        UUID reservedMember = member(graph.spaceId);
        UUID reservedBed = graph.newBed();
        ReserveOccupancyRequest reserve = new ReserveOccupancyRequest();
        reserve.setMemberId(reservedMember);
        reserve.setTargetType(AllocationTargetType.BED);
        reserve.setBedId(reservedBed);
        reserve.setMoveInDate(LocalDate.now().plusDays(3));
        occupancyService.reserve(graph.spaceId, graph.ownerId, reserve);
        var summary = dashboardSummaryService.getSummary(graph.spaceId, graph.ownerId, YearMonth.now().toString());
        assertThat(summary.getAccommodationOperations().getOccupiedBeds()).isEqualTo(1);
        assertThat(summary.getAccommodationOperations().getReservedBeds()).isEqualTo(1);
        assertThat(bedStatus(reservedBed)).isEqualTo("RESERVED");
    }

    @Test
    @Order(9)
    void corporateUnitAllocateAppearsInStayCharges() throws Exception {
        Graph graph = graph("CORPORATE_STAY", false, false);
        UUID memberId = member(graph.spaceId);
        UUID unitId = graph.newUnit();
        AllocateOccupancyRequest request = new AllocateOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.UNIT);
        request.setUnitId(unitId);
        request.setRentSnapshot(new BigDecimal("15000"));
        request.setDepositSnapshot(new BigDecimal("1000"));
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
        assertThat(occupancyTarget(occupancyId)).isEqualTo("UNIT");
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
        var charges = spacePaymentService.listActiveStayCharges(
                graph.spaceId, graph.ownerId, YearMonth.now().toString());
        assertThat(charges.getPayments()).anyMatch(payment -> occupancyId.equals(payment.getOccupancyId()));
    }

    @Test
    @Order(10)
    void bundledFoodDoesNotAddASecondLedgerLine() throws Exception {
        Graph graph = graph("PG", true, true);
        UUID memberId = member(graph.spaceId);
        AllocateOccupancyRequest rejected = bedRequest(memberId, graph.newBed());
        rejected.setRentSnapshot(new BigDecimal("12000"));
        rejected.setDepositSnapshot(BigDecimal.ZERO);
        rejected.setFoodIncludedInRent(true);
        rejected.setFoodChargeSnapshot(new BigDecimal("2500"));
        assertThatThrownBy(() -> occupancyService.allocate(graph.spaceId, graph.ownerId, rejected))
                .isInstanceOf(BusinessException.class);
        assertThat(memberPaymentCount(memberId)).isZero();
        AllocateOccupancyRequest accepted = bedRequest(memberId, rejected.getBedId());
        accepted.setRentSnapshot(new BigDecimal("12000"));
        accepted.setDepositSnapshot(new BigDecimal("1000"));
        accepted.setFoodIncludedInRent(true);
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, accepted).getOccupancyId();
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now());
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(spacePaymentCount(graph.spaceId, "MEAL")).isZero();
        assertThat(foodCharge(occupancyId)).isNull();
    }

    @Test
    @Order(11)
    void transferKeepDoesNotAddADepositAndDoesNotCopyThePlannedEnd() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID memberId = member(graph.spaceId);
        UUID firstBed = graph.newBed();
        UUID occupancyId = monthlyStay(graph, memberId, firstBed, new BigDecimal("7000"));
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        UUID nextBed = graph.newBed();
        TransferOccupancyRequest transfer = new TransferOccupancyRequest();
        transfer.setTargetType(AllocationTargetType.BED);
        transfer.setBedId(nextBed);
        transfer.setRentPolicy(TransferRentPolicy.KEEP);
        var moved = occupancyService.transfer(graph.spaceId, occupancyId, graph.ownerId, transfer);
        assertThat(moved.getOccupancyId()).isNotEqualTo(occupancyId);
        assertThat(memberPaymentCount(memberId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(dateColumn(moved.getOccupancyId(), "expected_exit_date")).isNull();
        assertThat(occupancyStatus(occupancyId)).isEqualTo("VACATED");
        assertThat(occupancyStatus(moved.getOccupancyId())).isEqualTo("ACTIVE");
    }

    @Test
    @Order(12)
    void concurrentSameKeyExtendCommitsOneHistoryRow() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID occupancyId = monthlyStay(graph, member(graph.spaceId), graph.newBed(), new BigDecimal("6000"));
        LocalDate end = LocalDate.now().plusDays(30);
        String key = "d4-" + occupancyId;
        List<Throwable> failures = race(
                () -> occupancyExtensionService.extend(graph.spaceId, occupancyId, graph.ownerId, extendRequest(end, key)),
                () -> occupancyExtensionService.extend(graph.spaceId, occupancyId, graph.ownerId, extendRequest(end, key)));
        assertThat(failures).isEmpty();
        assertThat(historyCount(occupancyId, key)).isEqualTo(1);
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(end);
    }

    @Test
    @Order(13)
    void concurrentDifferentExtendsLeaveNoExtra() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID occupancyId = monthlyStay(graph, member(graph.spaceId), graph.newBed(), new BigDecimal("6000"));
        LocalDate earlier = LocalDate.now().plusDays(25);
        LocalDate later = LocalDate.now().plusDays(50);
        List<Throwable> failures = race(
                () -> occupancyExtensionService.extend(
                        graph.spaceId, occupancyId, graph.ownerId, extendRequest(earlier, "d5-a-" + occupancyId)),
                () -> occupancyExtensionService.extend(
                        graph.spaceId, occupancyId, graph.ownerId, extendRequest(later, "d5-b-" + occupancyId)));
        assertThat(failures).hasSizeLessThanOrEqualTo(1);
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(later);
        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
    }

    @Test
    @Order(14)
    void concurrentExtendAndVacateLeaveAConsistentStayAndNoExtra() throws Exception {
        Graph graph = graph("PG", false, true);
        UUID occupancyId = monthlyStay(graph, member(graph.spaceId), graph.newBed(), new BigDecimal("6000"));
        List<Throwable> failures = race(
                () -> occupancyExtensionService.extend(
                        graph.spaceId,
                        occupancyId,
                        graph.ownerId,
                        extendRequest(LocalDate.now().plusDays(35), "d6-" + occupancyId)),
                () -> occupancyService.vacate(graph.spaceId, occupancyId, graph.ownerId, new VacateOccupancyRequest()));
        assertThat(failures).hasSizeLessThanOrEqualTo(1);
        assertThat(occupancyStatus(occupancyId)).isEqualTo("VACATED");
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
    }

    @Test
    @Order(15)
    void rentalUnitAllocateSyncsThePersistedLedger() throws Exception {
        Graph graph = graph("RENTAL", false, false);
        UUID memberId = member(graph.spaceId);
        UUID unitId = graph.newUnit();
        AllocateOccupancyRequest request = new AllocateOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.UNIT);
        request.setUnitId(unitId);
        request.setRentSnapshot(new BigDecimal("18000"));
        request.setDepositSnapshot(new BigDecimal("5000"));
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now());
        paymentGenerationService.syncExpectedPayments(graph.spaceId, graph.ownerId, YearMonth.now());
        assertThat(occupancyTarget(occupancyId)).isEqualTo("UNIT");
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
        assertThat(paymentStatus(occupancyId, "RENT", "MONTHLY")).isEqualTo("PENDING");
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("5000.00");
        assertThat(paymentStatus(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo("PENDING");
        var charges = spacePaymentService.listActiveStayCharges(
                graph.spaceId, graph.ownerId, YearMonth.now().toString());
        assertThat(charges.getPayments()).anyMatch(payment -> occupancyId.equals(payment.getOccupancyId()));
    }

    @Test
    @Order(16)
    void coLivingRoomTransferKeepLeavesOneDepositAndDoesNotCopyThePlannedEnd() throws Exception {
        Graph graph = graph("CO_LIVING", false, true);
        UUID memberId = member(graph.spaceId);
        graph.newBed();
        UUID occupancyId = monthlyStay(graph, memberId, graph.newBed(), new BigDecimal("9000"));
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(LocalDate.now().plusDays(15));
        assertThat(memberPaymentCount(memberId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        TransferOccupancyRequest transfer = new TransferOccupancyRequest();
        transfer.setTargetType(AllocationTargetType.ROOM);
        transfer.setRoomId(graph.roomId);
        transfer.setRentPolicy(TransferRentPolicy.KEEP);
        var moved = occupancyService.transfer(graph.spaceId, occupancyId, graph.ownerId, transfer);
        assertThat(moved.getOccupancyId()).isNotEqualTo(occupancyId);
        assertThat(occupancyTarget(moved.getOccupancyId())).isEqualTo("ROOM");
        assertThat(memberPaymentCount(memberId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentCount(moved.getOccupancyId(), "DEPOSIT", "SECURITY")).isZero();
        assertThat(dateColumn(moved.getOccupancyId(), "expected_exit_date")).isNull();
        assertThat(occupancyStatus(occupancyId)).isEqualTo("VACATED");
        assertThat(occupancyStatus(moved.getOccupancyId())).isEqualTo("ACTIVE");
    }

    @Test
    @Order(17)
    void concurrentSameKeyShortStayExtensionWritesOneExtra() throws Exception {
        Graph graph = graph("PG", false, true);
        ShortStay stay = shortStay(graph);
        LocalDate extended = stay.checkout.plusDays(1);
        if (com.acomi.acomi_backend.occupancy.application.support.StayExtensionRules.crossesCalendarMonth(
                stay.checkout, extended)) {
            throw new IllegalStateException("A one-night extension from the current checkout crosses a month");
        }
        String key = "short-same-" + stay.occupancyId;
        ExtendOccupancyRequest extend = shortExtend(extended, new BigDecimal("800"), key);
        List<Throwable> failures = race(
                () -> occupancyExtensionService.extend(graph.spaceId, stay.occupancyId, graph.ownerId, extend),
                () -> occupancyExtensionService.extend(graph.spaceId, stay.occupancyId, graph.ownerId, extend));
        assertThat(failures).isEmpty();
        occupancyExtensionService.extend(graph.spaceId, stay.occupancyId, graph.ownerId, extend);
        assertThat(historyCount(stay.occupancyId, key)).isEqualTo(1);
        assertThat(paymentCount(stay.occupancyId, "RENT", "EXTRA")).isEqualTo(2);
        assertThat(extraCountStarting(stay.occupancyId, stay.checkout)).isEqualTo(1);
        assertThat(extraAmountStarting(stay.occupancyId, stay.checkout)).isEqualByComparingTo("800.00");
        assertThat(extraStatusStarting(stay.occupancyId, stay.checkout)).isEqualTo("PENDING");
        assertThat(extraPeriodEndStarting(stay.occupancyId, stay.checkout)).isEqualTo(extended);
        assertThat(extraAmountEnding(stay.occupancyId, stay.checkout)).isEqualByComparingTo("5600.00");
        assertThat(extraStatusEnding(stay.occupancyId, stay.checkout)).isEqualTo("PENDING");
        assertThat(dateColumn(stay.occupancyId, "expected_exit_date")).isEqualTo(extended);
        assertThat(dateColumn(stay.occupancyId, "expected_checkout_date")).isEqualTo(extended);
        assertThat(paymentCount(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("2000.00");
        assertThat(paymentStatus(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualTo("PENDING");
        assertThat(paymentCount(stay.occupancyId, "RENT", "MONTHLY")).isZero();
        assertThat(occupancyStatus(stay.occupancyId)).isEqualTo("ACTIVE");
    }

    @Test
    @Order(18)
    void concurrentDifferentShortStayExtensionsLeaveOneAddedExtra() throws Exception {
        Graph graph = graph("PG", false, true);
        ShortStay stay = shortStay(graph);
        LocalDate extended = stay.checkout.plusDays(1);
        String firstKey = "short-a-" + stay.occupancyId;
        String secondKey = "short-b-" + stay.occupancyId;
        List<Throwable> failures = race(
                () -> occupancyExtensionService.extend(
                        graph.spaceId, stay.occupancyId, graph.ownerId,
                        shortExtend(extended, new BigDecimal("700"), firstKey)),
                () -> occupancyExtensionService.extend(
                        graph.spaceId, stay.occupancyId, graph.ownerId,
                        shortExtend(extended, new BigDecimal("900"), secondKey)));
        assertThat(failures).hasSize(1);
        assertThat(historyCount(stay.occupancyId, firstKey) + historyCount(stay.occupancyId, secondKey)).isEqualTo(1);
        assertThat(extraCountStarting(stay.occupancyId, stay.checkout)).isEqualTo(1);
        BigDecimal added = extraAmountStarting(stay.occupancyId, stay.checkout);
        assertThat(added.compareTo(new BigDecimal("700")) == 0 || added.compareTo(new BigDecimal("900")) == 0)
                .isTrue();
        assertThat(extraStatusStarting(stay.occupancyId, stay.checkout)).isEqualTo("PENDING");
        assertThat(extraPeriodEndStarting(stay.occupancyId, stay.checkout)).isEqualTo(extended);
        assertThat(dateColumn(stay.occupancyId, "expected_exit_date")).isEqualTo(extended);
        assertThat(dateColumn(stay.occupancyId, "expected_checkout_date")).isEqualTo(extended);
        assertThat(extraAmountEnding(stay.occupancyId, stay.checkout)).isEqualByComparingTo("5600.00");
        assertThat(paymentCount(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentCount(stay.occupancyId, "RENT", "MONTHLY")).isZero();
        assertThat(occupancyStatus(stay.occupancyId)).isEqualTo("ACTIVE");
    }

    @Test
    @Order(19)
    void concurrentCrossMonthShortStayExtensionsWriteNoExtra() throws Exception {
        Graph graph = graph("PG", false, true);
        ShortStay stay = shortStay(graph);
        LocalDate crossed = stay.checkout.plusMonths(1);
        String firstKey = "cross-a-" + stay.occupancyId;
        String secondKey = "cross-b-" + stay.occupancyId;
        List<Throwable> failures = race(
                () -> occupancyExtensionService.extend(
                        graph.spaceId, stay.occupancyId, graph.ownerId,
                        shortExtend(crossed, new BigDecimal("1600"), firstKey)),
                () -> occupancyExtensionService.extend(
                        graph.spaceId, stay.occupancyId, graph.ownerId,
                        shortExtend(crossed, new BigDecimal("1700"), secondKey)));
        assertThat(failures).hasSize(2);
        assertThatThrownBy(() -> occupancyExtensionService.extend(
                        graph.spaceId, stay.occupancyId, graph.ownerId,
                        shortExtend(crossed, new BigDecimal("1600"), firstKey)))
                .isInstanceOf(BusinessException.class);
        assertThat(historyCount(stay.occupancyId, firstKey)).isZero();
        assertThat(historyCount(stay.occupancyId, secondKey)).isZero();
        assertThat(paymentCount(stay.occupancyId, "RENT", "EXTRA")).isEqualTo(1);
        assertThat(paymentAmount(stay.occupancyId, "RENT", "EXTRA")).isEqualByComparingTo("5600.00");
        assertThat(paymentStatus(stay.occupancyId, "RENT", "EXTRA")).isEqualTo("PENDING");
        assertThat(paymentPeriodEnd(stay.occupancyId, "RENT", "EXTRA")).isEqualTo(stay.checkout);
        assertThat(paymentCount(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("2000.00");
        assertThat(paymentStatus(stay.occupancyId, "DEPOSIT", "SECURITY")).isEqualTo("PENDING");
        assertThat(dateColumn(stay.occupancyId, "expected_exit_date")).isEqualTo(stay.checkout);
        assertThat(dateColumn(stay.occupancyId, "expected_checkout_date")).isEqualTo(stay.checkout);
        assertThat(occupancyStatus(stay.occupancyId)).isEqualTo("ACTIVE");
        assertThat(paymentCount(stay.occupancyId, "RENT", "MONTHLY")).isZero();
    }

    @Test
    @Order(20)
    void coLivingRoomToBedTransferKeepLeavesOneDepositOnTheVacatedStay() throws Exception {
        Graph graph = graph("CO_LIVING", false, false);
        UUID memberId = member(graph.spaceId);
        UUID blockedBed = graph.newBed();
        UUID openBed = graph.newBed();
        accommodationStatusSyncService.setMaintenance(graph.spaceId, AllocationTargetType.BED, blockedBed);
        LocalDate checkout = LocalDate.now().plusDays(10);
        AllocateOccupancyRequest request = new AllocateOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.ROOM);
        request.setRoomId(graph.roomId);
        request.setRentSnapshot(new BigDecimal("8000"));
        request.setDepositSnapshot(new BigDecimal("1500"));
        request.setExpectedCheckoutDate(checkout);
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
        BigDecimal rentAmount = paymentAmount(occupancyId, "RENT", "MONTHLY");
        assertThat(occupancyTarget(occupancyId)).isEqualTo("ROOM");
        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(checkout);
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
        assertThat(paymentStatus(occupancyId, "RENT", "MONTHLY")).isEqualTo("PENDING");
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("1500.00");
        assertThat(paymentStatus(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo("PENDING");
        assertThat(bedStatus(openBed)).isEqualTo("AVAILABLE");
        UUID otherMember = member(graph.spaceId);
        AllocateOccupancyRequest blocked = bedRequest(otherMember, openBed);
        blocked.setRentSnapshot(new BigDecimal("4000"));
        blocked.setDepositSnapshot(new BigDecimal("100"));
        assertThatThrownBy(() -> occupancyService.allocate(graph.spaceId, graph.ownerId, blocked))
                .isInstanceOf(BusinessException.class);
        assertThat(memberPaymentCount(otherMember)).isZero();
        assertThat(memberPaymentCount(memberId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        TransferOccupancyRequest rejected = new TransferOccupancyRequest();
        rejected.setTargetType(AllocationTargetType.BED);
        rejected.setBedId(blockedBed);
        rejected.setRentPolicy(TransferRentPolicy.KEEP);
        assertThatThrownBy(() -> occupancyService.transfer(graph.spaceId, occupancyId, graph.ownerId, rejected))
                .isInstanceOf(BusinessException.class);
        assertThat(occupancyStatus(occupancyId)).isEqualTo("ACTIVE");
        assertThat(occupancyTarget(occupancyId)).isEqualTo("ROOM");
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(checkout);
        assertThat(paymentAmount(occupancyId, "RENT", "MONTHLY")).isEqualByComparingTo(rentAmount);
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentCount(occupancyId, "RENT", "EXTRA")).isZero();
        TransferOccupancyRequest transfer = new TransferOccupancyRequest();
        transfer.setTargetType(AllocationTargetType.BED);
        transfer.setBedId(openBed);
        transfer.setRentPolicy(TransferRentPolicy.KEEP);
        var moved = occupancyService.transfer(graph.spaceId, occupancyId, graph.ownerId, transfer);
        assertThat(moved.getOccupancyId()).isNotEqualTo(occupancyId);
        assertThat(occupancyTarget(moved.getOccupancyId())).isEqualTo("BED");
        assertThat(occupancyStatus(occupancyId)).isEqualTo("VACATED");
        assertThat(occupancyStatus(moved.getOccupancyId())).isEqualTo("ACTIVE");
        assertThat(dateColumn(occupancyId, "expected_exit_date")).isEqualTo(checkout);
        assertThat(dateColumn(moved.getOccupancyId(), "expected_exit_date")).isNull();
        assertThat(memberPaymentCount(memberId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentCount(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "DEPOSIT", "SECURITY")).isEqualByComparingTo("1500.00");
        assertThat(paymentStatus(occupancyId, "DEPOSIT", "SECURITY")).isEqualTo("PENDING");
        assertThat(paymentCount(moved.getOccupancyId(), "DEPOSIT", "SECURITY")).isZero();
        assertThat(paymentCount(moved.getOccupancyId(), "RENT", "EXTRA")).isZero();
        assertThat(paymentCount(occupancyId, "RENT", "MONTHLY")).isEqualTo(1);
        assertThat(paymentAmount(occupancyId, "RENT", "MONTHLY")).isEqualByComparingTo(rentAmount);
        assertThat(paymentStatus(occupancyId, "RENT", "MONTHLY")).isEqualTo("PENDING");
        assertThat(paymentCount(moved.getOccupancyId(), "RENT", "MONTHLY")).isZero();
        assertThat(historyEventCount(moved.getOccupancyId(), "TRANSFERRED")).isEqualTo(1);
        assertThat(historyEventCount(occupancyId, "EXTENDED")).isZero();
    }

    private ShortStay shortStay(Graph graph) throws Exception {
        UUID memberId = member(graph.spaceId);
        LocalDate checkout = sameMonthCheckout();
        AllocateOccupancyRequest request = bedRequest(memberId, graph.newBed());
        request.setStayKind(StayKind.SHORT);
        request.setAgreedTotal(new BigDecimal("5600"));
        request.setDepositSnapshot(new BigDecimal("2000"));
        request.setExpectedCheckoutDate(checkout);
        UUID occupancyId = occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
        return new ShortStay(occupancyId, checkout);
    }

    private static ExtendOccupancyRequest shortExtend(LocalDate end, BigDecimal amount, String key) {
        ExtendOccupancyRequest request = extendRequest(end, key);
        request.setAdditionalAmount(amount);
        return request;
    }

    private record ShortStay(UUID occupancyId, LocalDate checkout) {}

    private UUID monthlyStay(Graph graph, UUID memberId, UUID bedId, BigDecimal rent) {
        AllocateOccupancyRequest request = bedRequest(memberId, bedId);
        request.setRentSnapshot(rent);
        request.setDepositSnapshot(new BigDecimal("500"));
        request.setExpectedCheckoutDate(LocalDate.now().plusDays(15));
        return occupancyService.allocate(graph.spaceId, graph.ownerId, request).getOccupancyId();
    }

    private static AllocateOccupancyRequest bedRequest(UUID memberId, UUID bedId) {
        AllocateOccupancyRequest request = new AllocateOccupancyRequest();
        request.setMemberId(memberId);
        request.setTargetType(AllocationTargetType.BED);
        request.setBedId(bedId);
        return request;
    }

    private static ExtendOccupancyRequest extendRequest(LocalDate end, String key) {
        ExtendOccupancyRequest request = new ExtendOccupancyRequest();
        request.setExpectedExitDate(end);
        request.setIdempotencyKey(key);
        return request;
    }

    private static LocalDate sameMonthCheckout() {
        LocalDate today = LocalDate.now();
        LocalDate checkout = today.plusDays(1);
        if (!YearMonth.from(checkout).equals(YearMonth.from(today))) {
            throw new IllegalStateException(
                    "D-05 rejects a cross-month short stay, and today is the last day of the month");
        }
        return checkout;
    }

    private void asOwner(UUID ownerId) {
        UserEntity owner = userRepository.findById(ownerId).orElseThrow();
        UserPrincipal principal = new UserPrincipal(owner);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private List<Throwable> race(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = new ArrayList<>();
        try {
            Future<?> left = pool.submit(racing(start, first, failures));
            Future<?> right = pool.submit(racing(start, second, failures));
            start.countDown();
            left.get();
            right.get();
        } finally {
            pool.shutdownNow();
        }
        return failures;
    }

    private static Runnable racing(CountDownLatch start, Runnable body, List<Throwable> failures) {
        return () -> {
            try {
                start.await();
                body.run();
            } catch (Throwable failure) {
                synchronized (failures) {
                    failures.add(failure);
                }
            }
        };
    }

    private Graph graph(String type, boolean foodIncluded, boolean shortStay) throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            insert(connection, "users", Map.of(
                    "id", ownerId,
                    "mobile_number", mobile(),
                    "full_name", "Service Owner",
                    "reference_code", code("USR"),
                    "is_active", true));
            Map<String, Object> space = new LinkedHashMap<>();
            space.put("id", spaceId);
            space.put("owner_id", ownerId);
            space.put("name", "Service " + type);
            space.put("type", type);
            space.put("reference_code", code("SPC"));
            space.put("is_active", true);
            space.put("food_included_in_rent", foodIncluded);
            space.put("short_stay_enabled", shortStay);
            insert(connection, "spaces", space);
            insert(connection, "space_memberships", Map.of(
                    "id", UUID.randomUUID(),
                    "user_id", ownerId,
                    "space_id", spaceId,
                    "role", "OWNER",
                    "status", "ACTIVE"));
            if (!"MESS".equals(type)) {
                insert(connection, "buildings", Map.of(
                        "id", buildingId,
                        "space_id", spaceId,
                        "name", "Block",
                        "reference_code", code("BLD"),
                        "is_active", true));
                insert(connection, "floors", Map.of(
                        "id", floorId,
                        "building_id", buildingId,
                        "name", "One",
                        "floor_number", 1,
                        "reference_code", code("FLR"),
                        "is_active", true));
                if (!"CORPORATE_STAY".equals(type) && !"RENTAL".equals(type)) {
                    insert(connection, "rooms", Map.of(
                            "id", roomId,
                            "floor_id", floorId,
                            "name", "Room",
                            "room_number", code("R"),
                            "room_type", "SHARED",
                            "reference_code", code("ROM"),
                            "is_active", true,
                            "status", "AVAILABLE"));
                }
            }
        }
        return new Graph(ownerId, spaceId, buildingId, floorId, roomId);
    }

    private UUID member(UUID spaceId) throws Exception {
        UUID memberId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            insert(connection, "members", Map.of(
                    "id", memberId,
                    "space_id", spaceId,
                    "full_name", "Resident",
                    "mobile_number", mobile(),
                    "role", "TENANT",
                    "reference_code", code("MEM"),
                    "is_active", true));
        }
        return memberId;
    }

    private void insert(Connection connection, String table, Map<String, Object> values) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>(values);
        try (PreparedStatement required = connection.prepareStatement(
                """
                SELECT column_name, data_type, udt_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND is_nullable = 'NO' AND column_default IS NULL
                """)) {
            required.setString(1, table);
            try (ResultSet columns = required.executeQuery()) {
                while (columns.next()) {
                    String name = columns.getString("column_name");
                    if (!row.containsKey(name)) {
                        row.put(name, fallback(table, name, columns.getString("data_type")));
                    }
                }
            }
        }
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(key -> "?").toList());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")")) {
            int index = 1;
            for (Object value : row.values()) {
                if (value instanceof LocalDate date) {
                    statement.setObject(index++, date);
                } else if (value instanceof LocalDateTime time) {
                    statement.setTimestamp(index++, Timestamp.valueOf(time));
                } else {
                    statement.setObject(index++, value);
                }
            }
            statement.executeUpdate();
        }
    }

    private static Object fallback(String table, String column, String dataType) {
        if ("uuid".equals(dataType)) {
            throw new IllegalStateException("Missing required id " + table + "." + column);
        }
        return switch (column) {
            case "system_role" -> "USER";
            case "profile_status" -> "PENDING";
            case "kyc_status" -> "NOT_STARTED";
            case "status" -> "ACTIVE";
            case "role" -> "TENANT";
            case "type" -> "PG";
            case "room_type" -> "SHARED";
            case "enquiry_emails" -> "[]";
            default -> switch (dataType) {
                case "boolean" -> false;
                case "integer", "smallint", "bigint" -> 0;
                case "numeric" -> 0;
                case "date" -> LocalDate.now();
                case "timestamp without time zone", "timestamp with time zone" -> Timestamp.valueOf(LocalDateTime.now());
                case "jsonb", "json" -> "[]";
                case "ARRAY" -> "{}";
                case "text", "character varying" -> "svc";
                default -> throw new IllegalStateException("Missing required " + table + "." + column + " (" + dataType + ")");
            };
        };
    }

    private UUID soleOccupancy(UUID memberId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM occupancies WHERE member_id = ?")) {
            statement.setObject(1, memberId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private String stayKind(UUID occupancyId) throws Exception {
        return textColumn(occupancyId, "stay_kind");
    }

    private String occupancyStatus(UUID occupancyId) throws Exception {
        return textColumn(occupancyId, "status");
    }

    private String occupancyTarget(UUID occupancyId) throws Exception {
        return textColumn(occupancyId, "target_type");
    }

    private String textColumn(UUID occupancyId, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + column + " FROM occupancies WHERE id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private LocalDate dateColumn(UUID occupancyId, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + column + " FROM occupancies WHERE id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, LocalDate.class);
            }
        }
    }

    private BigDecimal foodCharge(UUID occupancyId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT food_charge_snapshot FROM occupancies WHERE id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private int paymentCount(UUID occupancyId, String type, String category) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int memberPaymentCount(UUID memberId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM space_payments WHERE member_id = ?")) {
            statement.setObject(1, memberId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int memberPaymentCount(UUID memberId, String type, String category) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM space_payments
                        WHERE member_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, memberId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int spacePaymentCount(UUID spaceId, String type) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM space_payments WHERE space_id = ? AND payment_type = ?")) {
            statement.setObject(1, spaceId);
            statement.setString(2, type);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private BigDecimal paymentAmount(UUID occupancyId, String type, String category) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT amount FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private BigDecimal paymentAmountForMonth(UUID occupancyId, String type, String category, String month)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT amount FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ? AND month = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            statement.setString(4, month);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).as(type + " " + category + " " + month).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private String paymentStatusForMonth(UUID occupancyId, String type, String category, String month)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT payment_status FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ? AND month = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            statement.setString(4, month);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private String paymentStatus(UUID occupancyId, String type, String category) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT payment_status FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private LocalDate paymentPeriodEnd(UUID occupancyId, String type, String category) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT billing_period_end FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, LocalDate.class);
            }
        }
    }

    private UUID paymentId(UUID occupancyId, String type, String category) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT id FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private int historyCount(UUID occupancyId, String key) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM occupancy_history
                        WHERE occupancy_id = ? AND idempotency_key = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, key);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int historyEventCount(UUID occupancyId, String eventType) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM occupancy_history
                        WHERE occupancy_id = ? AND event_type = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, eventType);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private int extraCountStarting(UUID occupancyId, LocalDate periodStart) throws Exception {
        return extraInt(occupancyId, periodStart, "COUNT(*)", "billing_period_start");
    }

    private BigDecimal extraAmountStarting(UUID occupancyId, LocalDate periodStart) throws Exception {
        return extraDecimal(occupancyId, periodStart, "amount", "billing_period_start");
    }

    private String extraStatusStarting(UUID occupancyId, LocalDate periodStart) throws Exception {
        return extraString(occupancyId, periodStart, "payment_status", "billing_period_start");
    }

    private LocalDate extraPeriodEndStarting(UUID occupancyId, LocalDate periodStart) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = extraByStart(connection, occupancyId, periodStart, "billing_period_end")) {
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getObject(1, LocalDate.class);
            }
        }
    }

    private BigDecimal extraAmountEnding(UUID occupancyId, LocalDate periodEnd) throws Exception {
        return extraDecimal(occupancyId, periodEnd, "amount", "billing_period_end");
    }

    private String extraStatusEnding(UUID occupancyId, LocalDate periodEnd) throws Exception {
        return extraString(occupancyId, periodEnd, "payment_status", "billing_period_end");
    }

    private int extraInt(UUID occupancyId, LocalDate day, String column, String match) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = extraByStart(connection, occupancyId, day, column, match)) {
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private BigDecimal extraDecimal(UUID occupancyId, LocalDate day, String column, String match) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = extraByStart(connection, occupancyId, day, column, match)) {
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private String extraString(UUID occupancyId, LocalDate day, String column, String match) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = extraByStart(connection, occupancyId, day, column, match)) {
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private PreparedStatement extraByStart(
            Connection connection, UUID occupancyId, LocalDate day, String column) throws SQLException {
        return extraByStart(connection, occupancyId, day, column, "billing_period_start");
    }

    private PreparedStatement extraByStart(
            Connection connection, UUID occupancyId, LocalDate day, String column, String match) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM space_payments WHERE occupancy_id = ? AND payment_type = 'RENT' "
                        + "AND payment_category = 'EXTRA' AND " + match + " = ?");
        statement.setObject(1, occupancyId);
        statement.setObject(2, day);
        return statement;
    }

    private String bedStatus(UUID bedId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM beds WHERE id = ?")) {
            statement.setObject(1, bedId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static String code(String prefix) {
        return prefix + String.format("%05d", CODES.incrementAndGet());
    }

    private static String mobile() {
        return String.format("9%09d", CODES.incrementAndGet());
    }

    private final class Graph {
        final UUID ownerId;
        final UUID spaceId;
        final UUID buildingId;
        final UUID floorId;
        final UUID roomId;

        private Graph(UUID ownerId, UUID spaceId, UUID buildingId, UUID floorId, UUID roomId) {
            this.ownerId = ownerId;
            this.spaceId = spaceId;
            this.buildingId = buildingId;
            this.floorId = floorId;
            this.roomId = roomId;
        }

        UUID newBed() throws Exception {
            UUID bedId = UUID.randomUUID();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                insert(connection, "beds", Map.of(
                        "id", bedId,
                        "room_id", roomId,
                        "name", "Bed",
                        "bed_number", String.valueOf(CODES.incrementAndGet()),
                        "reference_code", code("BED"),
                        "is_active", true,
                        "status", "AVAILABLE"));
            }
            return bedId;
        }

        UUID newUnit() throws Exception {
            UUID unitId = UUID.randomUUID();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(true);
                insert(connection, "units", Map.of(
                        "id", unitId,
                        "building_id", buildingId,
                        "floor_id", floorId,
                        "name", "Unit",
                        "unit_number", String.valueOf(CODES.incrementAndGet()),
                        "reference_code", code("UNT"),
                        "is_active", true,
                        "status", "AVAILABLE"));
            }
            return unitId;
        }
    }
}
