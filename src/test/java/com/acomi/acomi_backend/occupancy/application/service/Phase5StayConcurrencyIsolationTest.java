package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acomi.acomi_backend.common.exception.BusinessException;
import com.acomi.acomi_backend.occupancy.application.support.StayAvailabilityRules;
import com.acomi.acomi_backend.occupancy.domain.model.AllocationTargetType;
import com.acomi.acomi_backend.occupancy.domain.model.OccupancyStatus;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.entity.OccupancyEntity;
import com.acomi.acomi_backend.occupancy.infrastructure.persistence.repository.OccupancyRepository;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Two real PostgreSQL transactions on a database created for this run.
 * Does not connect to {@code acomi_db}.
 */
@EnabledIf("postgresAvailable")
class Phase5StayConcurrencyIsolationTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_7 = LocalDate.of(2026, 11, 7);
    private static final LocalDate NOV_8 = LocalDate.of(2026, 11, 8);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);
    private static final LocalDate NOV_12 = LocalDate.of(2026, 11, 12);

    private static Phase5IsolatedDatabase database;
    private static final ThreadLocal<Connection> CONNECTIONS = new ThreadLocal<>();
    private static final AtomicInteger CODES = new AtomicInteger(100);

    @BeforeAll
    static void createIsolatedDatabase() throws Exception {
        database = Phase5IsolatedDatabase.open();
        System.out.println("PHASE5_RACE_DB=" + database.name);
        assertThat(database.sharedDatabaseStillPresent()).isTrue();
    }

    @AfterAll
    static void dropIsolatedDatabase() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    static boolean postgresAvailable() {
        return Phase5IsolatedDatabase.postgresAvailable();
    }

    @Test
    void twoOverlappingActiveInserts_oneCommitsAndTheLoserRollsBack() throws Exception {
        Fixture fixture = Fixture.create(database);
        UUID winnerMember = fixture.memberA;
        UUID loserMember = fixture.memberB;
        CountDownLatch inserted = new CountDownLatch(1);
        AtomicReference<Boolean> winnerCommitted = new AtomicReference<>();
        AtomicReference<Boolean> loserRolledBack = new AtomicReference<>();

        Thread winner = new Thread(() -> run(() -> {
            Connection connection = CONNECTIONS.get();
            insertActive(connection, fixture, winnerMember, NOV_1, NOV_8);
            inserted.countDown();
            connection.commit();
            winnerCommitted.set(true);
        }));
        Thread loser = new Thread(() -> run(() -> {
            inserted.await();
            Connection connection = CONNECTIONS.get();
            try {
                insertActive(connection, fixture, loserMember, NOV_1, NOV_10);
                connection.commit();
                loserRolledBack.set(false);
            } catch (SQLException ex) {
                connection.rollback();
                loserRolledBack.set("23505".equals(ex.getSQLState()));
                if (!"23505".equals(ex.getSQLState())) {
                    throw ex;
                }
            }
        }));
        join(winner, loser);

        assertThat(winnerCommitted.get()).isTrue();
        assertThat(loserRolledBack.get()).isTrue();
        assertThat(countOccupancies(fixture.bedId)).isEqualTo(1);
        assertThat(memberStatus(loserMember)).isEqualTo("VACATED");
        assertThat(bedStatus(fixture.bedId)).isEqualTo("AVAILABLE");
        assertIndexesRemain();
    }

    @Test
    void twoOverlappingReservations_oneCommitsAndTheLoserRollsBack() throws Exception {
        Fixture fixture = Fixture.create(database);
        CountDownLatch inserted = new CountDownLatch(1);
        AtomicReference<Boolean> loserRolledBack = new AtomicReference<>();

        Thread winner = new Thread(() -> run(() -> {
            Connection connection = CONNECTIONS.get();
            insertStay(connection, fixture, fixture.memberA, OccupancyStatus.RESERVED, NOV_1, NOV_8, null);
            inserted.countDown();
            connection.commit();
        }));
        Thread loser = new Thread(() -> run(() -> {
            inserted.await();
            Connection connection = CONNECTIONS.get();
            try {
                insertStay(connection, fixture, fixture.memberB, OccupancyStatus.RESERVED, NOV_1, NOV_10, null);
                connection.commit();
                loserRolledBack.set(false);
            } catch (SQLException ex) {
                connection.rollback();
                loserRolledBack.set("23505".equals(ex.getSQLState()));
                if (!"23505".equals(ex.getSQLState())) {
                    throw ex;
                }
            }
        }));
        join(winner, loser);

        assertThat(loserRolledBack.get()).isTrue();
        assertThat(countOccupancies(fixture.bedId)).isEqualTo(1);
        assertThat(memberStatus(fixture.memberB)).isEqualTo("VACATED");
        assertThat(bedStatus(fixture.bedId)).isEqualTo("AVAILABLE");
    }

    @Test
    void extensionLosesToAnOverlappingReservation_andWritesNoCharge() throws Exception {
        Fixture fixture = Fixture.create(database);
        UUID activeId = insertCommitted(fixture, fixture.memberA, OccupancyStatus.ACTIVE, NOV_1, NOV_8, null);
        CountDownLatch reserved = new CountDownLatch(1);

        Thread reservation = new Thread(() -> run(() -> {
            Connection connection = CONNECTIONS.get();
            insertStay(connection, fixture, fixture.memberB, OccupancyStatus.RESERVED, NOV_1, NOV_10, null);
            connection.commit();
            reserved.countDown();
        }));
        AtomicReference<Boolean> extensionRolledBack = new AtomicReference<>();
        Thread extension = new Thread(() -> run(() -> {
            reserved.await();
            Connection connection = CONNECTIONS.get();
            lockOccupancy(connection, activeId);
            boolean overlap = guardOverlaps(fixture, NOV_1, NOV_12, activeId);
            if (overlap) {
                connection.rollback();
                extensionRolledBack.set(true);
                return;
            }
            updateEnd(connection, activeId, NOV_12);
            insertCharge(connection, fixture, activeId);
            connection.commit();
            extensionRolledBack.set(false);
        }));
        join(reservation, extension);

        assertThat(extensionRolledBack.get()).isTrue();
        assertThat(plannedEnd(activeId)).isEqualTo(NOV_8);
        assertThat(chargeCount(activeId)).isZero();
        assertThat(countByStatus(fixture.bedId, "ACTIVE")).isEqualTo(1);
        assertThat(countByStatus(fixture.bedId, "RESERVED")).isEqualTo(1);
        assertThat(bedStatus(fixture.bedId)).isEqualTo("AVAILABLE");
    }

    @Test
    void reservationLosesToAnOverlappingExtension_andWritesNoReservation() throws Exception {
        Fixture fixture = Fixture.create(database);
        UUID activeId = insertCommitted(fixture, fixture.memberA, OccupancyStatus.ACTIVE, NOV_1, NOV_8, null);
        CountDownLatch locked = new CountDownLatch(1);

        Thread extension = new Thread(() -> run(() -> {
            Connection connection = CONNECTIONS.get();
            lockOccupancy(connection, activeId);
            locked.countDown();
            if (guardOverlaps(fixture, NOV_1, NOV_12, activeId)) {
                connection.rollback();
                return;
            }
            updateEnd(connection, activeId, NOV_12);
            insertCharge(connection, fixture, activeId);
            connection.commit();
        }));
        AtomicReference<Boolean> reservationRolledBack = new AtomicReference<>();
        Thread reservation = new Thread(() -> run(() -> {
            locked.await();
            Connection connection = CONNECTIONS.get();
            boolean overlap = guardOverlaps(fixture, NOV_1, NOV_10, null);
            if (overlap) {
                connection.rollback();
                reservationRolledBack.set(true);
                return;
            }
            insertStay(connection, fixture, fixture.memberB, OccupancyStatus.RESERVED, NOV_1, NOV_10, null);
            connection.commit();
            reservationRolledBack.set(false);
        }));
        join(extension, reservation);

        assertThat(reservationRolledBack.get()).isTrue();
        assertThat(plannedEnd(activeId)).isEqualTo(NOV_12);
        assertThat(chargeCount(activeId)).isEqualTo(1);
        assertThat(countByStatus(fixture.bedId, "RESERVED")).isZero();
        assertThat(countOccupancies(fixture.bedId)).isEqualTo(1);
    }

    @Test
    void adjacentHalfOpenStayCommits_andAnOverlappingVacatedStayRollsBack() throws Exception {
        Fixture fixture = Fixture.create(database);
        UUID vacatedId = insertCommitted(
                fixture, fixture.memberA, OccupancyStatus.VACATED, NOV_1, null, NOV_8.atStartOfDay());

        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            CONNECTIONS.set(connection);
            try {
                assertThat(guardOverlaps(fixture, NOV_8, NOV_10, null)).isFalse();
                insertStay(connection, fixture, fixture.memberB, OccupancyStatus.ACTIVE, NOV_8, NOV_10, null);
                connection.commit();
            } finally {
                CONNECTIONS.remove();
            }
        }
        assertThat(countOccupancies(fixture.bedId)).isEqualTo(2);

        Fixture overlap = Fixture.create(database);
        insertCommitted(overlap, overlap.memberA, OccupancyStatus.VACATED, NOV_1, null, NOV_8.atStartOfDay());
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            CONNECTIONS.set(connection);
            try {
                assertThat(guardOverlaps(overlap, NOV_7, NOV_10, null)).isTrue();
                assertThatThrownBy(() -> guard(overlap).assertAvailable(
                                AllocationTargetType.BED,
                                overlap.bedId,
                                overlap.roomId,
                                null,
                                NOV_7,
                                NOV_10,
                                null))
                        .isInstanceOf(BusinessException.class)
                        .hasMessage(StayAvailabilityRules.OVERLAP);
                connection.rollback();
            } finally {
                CONNECTIONS.remove();
            }
        }
        assertThat(countOccupancies(overlap.bedId)).isEqualTo(1);
        assertThat(plannedEnd(vacatedId)).isNull();
    }

    private static boolean guardOverlaps(Fixture fixture, LocalDate start, LocalDate end, UUID excludeId)
            throws Exception {
        try {
            guard(fixture).assertAvailable(
                    AllocationTargetType.BED, fixture.bedId, fixture.roomId, null, start, end, excludeId);
            return false;
        } catch (BusinessException ex) {
            if (!StayAvailabilityRules.OVERLAP.equals(ex.getMessage())) {
                throw ex;
            }
            return true;
        }
    }

    private static StayRangeGuard guard(Fixture fixture) {
        OccupancyRepository repository = (OccupancyRepository) Proxy.newProxyInstance(
                OccupancyRepository.class.getClassLoader(),
                new Class<?>[] {OccupancyRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByBed_IdAndStatusIn" -> lockBed(fixture.bedId);
                    case "findByRoom_IdAndTargetTypeAndStatusIn" -> List.of();
                    case "findByUnit_IdAndStatusIn" -> List.of();
                    case "toString" -> "phase5-race-repository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new StayRangeGuard(repository);
    }

    private static List<OccupancyEntity> lockBed(UUID bedId) throws SQLException {
        Connection connection = CONNECTIONS.get();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id, status, move_in_date, expected_exit_date, expected_checkout_date, vacated_at
                FROM occupancies
                WHERE bed_id = ? AND status IN ('ACTIVE', 'RESERVED', 'VACATED')
                FOR UPDATE
                """)) {
            statement.setObject(1, bedId);
            try (ResultSet rows = statement.executeQuery()) {
                List<OccupancyEntity> found = new ArrayList<>();
                while (rows.next()) {
                    OccupancyEntity occupancy = new OccupancyEntity();
                    occupancy.setId(rows.getObject("id", UUID.class));
                    occupancy.setStatus(OccupancyStatus.valueOf(rows.getString("status")));
                    occupancy.setMoveInDate(rows.getObject("move_in_date", LocalDate.class));
                    occupancy.setExpectedExitDate(rows.getObject("expected_exit_date", LocalDate.class));
                    occupancy.setExpectedCheckoutDate(rows.getObject("expected_checkout_date", LocalDate.class));
                    Timestamp vacated = rows.getTimestamp("vacated_at");
                    occupancy.setVacatedAt(vacated == null ? null : vacated.toLocalDateTime());
                    found.add(occupancy);
                }
                return found;
            }
        }
    }

    private static void lockOccupancy(Connection connection, UUID occupancyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM occupancies WHERE id = ? FOR UPDATE")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Occupancy disappeared: " + occupancyId);
                }
            }
        }
    }

    private static void insertActive(Connection connection, Fixture fixture, UUID memberId, LocalDate start, LocalDate end)
            throws SQLException {
        insertStay(connection, fixture, memberId, OccupancyStatus.ACTIVE, start, end, null);
    }

    private static UUID insertStay(
            Connection connection,
            Fixture fixture,
            UUID memberId,
            OccupancyStatus status,
            LocalDate start,
            LocalDate end,
            LocalDateTime vacatedAt)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO occupancies (
                    id, reference_code, space_id, member_id, target_type, building_id, floor_id, room_id, bed_id,
                    allocated_at, allocated_by, move_in_date, expected_exit_date, expected_checkout_date,
                    vacated_at, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, 'BED', ?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, id);
            statement.setString(2, code("STY"));
            statement.setObject(3, fixture.spaceId);
            statement.setObject(4, memberId);
            statement.setObject(5, fixture.buildingId);
            statement.setObject(6, fixture.floorId);
            statement.setObject(7, fixture.roomId);
            statement.setObject(8, fixture.bedId);
            statement.setObject(9, fixture.userId);
            statement.setObject(10, start);
            statement.setObject(11, end);
            statement.setObject(12, end);
            statement.setTimestamp(13, vacatedAt == null ? null : Timestamp.valueOf(vacatedAt));
            statement.setString(14, status.name());
            statement.setObject(15, fixture.userId);
            statement.setObject(16, fixture.userId);
            statement.executeUpdate();
        }
        return id;
    }

    private static void updateEnd(Connection connection, UUID occupancyId, LocalDate end) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE occupancies SET expected_exit_date = ?, expected_checkout_date = ? WHERE id = ?")) {
            statement.setObject(1, end);
            statement.setObject(2, end);
            statement.setObject(3, occupancyId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("End date was not updated");
            }
        }
    }

    private static void insertCharge(Connection connection, Fixture fixture, UUID occupancyId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO space_payments (
                    id, space_id, member_id, occupancy_id, payment_type, payment_category, title,
                    amount, due_date, month, payment_status, billing_period_start, billing_period_end)
                VALUES (?, ?, ?, ?, 'RENT', 'EXTRA', 'Stay extension', 100, ?, '2026-11', 'PENDING', ?, ?)
                """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, fixture.spaceId);
            statement.setObject(3, fixture.memberA);
            statement.setObject(4, occupancyId);
            statement.setObject(5, NOV_8);
            statement.setObject(6, NOV_8);
            statement.setObject(7, NOV_12);
            statement.executeUpdate();
        }
    }

    private static int countOccupancies(UUID bedId) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM occupancies WHERE bed_id = ?")) {
            statement.setObject(1, bedId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static int countByStatus(UUID bedId, String status) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM occupancies WHERE bed_id = ? AND status = ?")) {
            statement.setObject(1, bedId);
            statement.setString(2, status);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static int chargeCount(UUID occupancyId) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM space_payments WHERE occupancy_id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static LocalDate plannedEnd(UUID occupancyId) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT expected_exit_date FROM occupancies WHERE id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getObject(1, LocalDate.class);
            }
        }
    }

    private static String memberStatus(UUID memberId) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT occupancy_status FROM members WHERE id = ?")) {
            statement.setObject(1, memberId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static String bedStatus(UUID bedId) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement("SELECT status FROM beds WHERE id = ?")) {
            statement.setObject(1, bedId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static void assertIndexesRemain() throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT indexname FROM pg_indexes WHERE indexname LIKE 'uq_occupancies_%'")) {
            try (ResultSet rows = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rows.next()) {
                    names.add(rows.getString(1));
                }
                assertThat(names).contains(
                        "uq_occupancies_member_active",
                        "uq_occupancies_member_reserved",
                        "uq_occupancies_bed_active",
                        "uq_occupancies_bed_reserved",
                        "uq_occupancies_room_active",
                        "uq_occupancies_room_reserved",
                        "uq_occupancies_unit_active",
                        "uq_occupancies_unit_reserved");
            }
        }
    }

    private static void run(SqlRunnable body) {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (java.sql.Statement timeout = connection.createStatement()) {
                timeout.execute("SET lock_timeout = '8s'");
            }
            CONNECTIONS.set(connection);
            try {
                body.run();
            } finally {
                CONNECTIONS.remove();
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void join(Thread first, Thread second) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread.UncaughtExceptionHandler handler = (thread, error) -> failure.set(error);
        first.setUncaughtExceptionHandler(handler);
        second.setUncaughtExceptionHandler(handler);
        first.start();
        second.start();
        first.join(20000);
        second.join(20000);
        assertThat(first.isAlive()).isFalse();
        assertThat(second.isAlive()).isFalse();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws Exception;
    }

    private static final class Fixture {
        final UUID userId;
        final UUID spaceId;
        final UUID memberA;
        final UUID memberB;
        final UUID buildingId;
        final UUID floorId;
        final UUID roomId;
        final UUID bedId;

        private Fixture(
                UUID userId,
                UUID spaceId,
                UUID memberA,
                UUID memberB,
                UUID buildingId,
                UUID floorId,
                UUID roomId,
                UUID bedId) {
            this.userId = userId;
            this.spaceId = spaceId;
            this.memberA = memberA;
            this.memberB = memberB;
            this.buildingId = buildingId;
            this.floorId = floorId;
            this.roomId = roomId;
            this.bedId = bedId;
        }

        static Fixture create(Phase5IsolatedDatabase database) throws Exception {
            UUID userId = UUID.randomUUID();
            UUID spaceId = UUID.randomUUID();
            UUID memberA = UUID.randomUUID();
            UUID memberB = UUID.randomUUID();
            UUID buildingId = UUID.randomUUID();
            UUID floorId = UUID.randomUUID();
            UUID roomId = UUID.randomUUID();
            UUID bedId = UUID.randomUUID();
            try (Connection connection = database.connect()) {
                connection.setAutoCommit(true);
                insert(connection, "users", Map.of(
                        "id", userId,
                        "mobile_number", mobile(),
                        "full_name", "Race User",
                        "reference_code", code("USR")));
                insert(connection, "spaces", Map.of(
                        "id", spaceId,
                        "owner_id", userId,
                        "name", "Race Space",
                        "type", "PG",
                        "reference_code", code("SPC")));
                insert(connection, "members", Map.of(
                        "id", memberA,
                        "space_id", spaceId,
                        "full_name", "Member A",
                        "mobile_number", mobile(),
                        "role", "TENANT",
                        "reference_code", code("MEM")));
                insert(connection, "members", Map.of(
                        "id", memberB,
                        "space_id", spaceId,
                        "full_name", "Member B",
                        "mobile_number", mobile(),
                        "role", "TENANT",
                        "reference_code", code("MEM")));
                insert(connection, "buildings", Map.of(
                        "id", buildingId,
                        "space_id", spaceId,
                        "name", "Block",
                        "reference_code", code("BLD")));
                insert(connection, "floors", Map.of(
                        "id", floorId,
                        "building_id", buildingId,
                        "name", "One",
                        "floor_number", 1,
                        "reference_code", code("FLR")));
                insert(connection, "rooms", Map.of(
                        "id", roomId,
                        "floor_id", floorId,
                        "name", "Room",
                        "room_number", code("R"),
                        "room_type", "SHARED",
                        "reference_code", code("ROM")));
                insert(connection, "beds", Map.of(
                        "id", bedId,
                        "room_id", roomId,
                        "name", "Bed",
                        "bed_number", "1",
                        "reference_code", code("BED")));
            }
            return new Fixture(userId, spaceId, memberA, memberB, buildingId, floorId, roomId, bedId);
        }
    }

    private static void insert(Connection connection, String table, Map<String, Object> values) throws SQLException {
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
                    if (row.containsKey(name)) {
                        continue;
                    }
                    row.put(name, fallback(table, name, columns.getString("data_type"), columns.getString("udt_name")));
                }
            }
        }
        String columns = String.join(", ", row.keySet());
        String placeholders = String.join(", ", row.keySet().stream().map(key -> "?").toList());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (" + columns + ") VALUES (" + placeholders + ")")) {
            int index = 1;
            for (Object value : row.values()) {
                statement.setObject(index++, value);
            }
            statement.executeUpdate();
        }
    }

    private static Object fallback(String table, String column, String dataType, String udt) {
        if ("uuid".equals(dataType) || "uuid".equals(udt)) {
            throw new IllegalStateException("Missing required id " + table + "." + column);
        }
        return switch (column) {
            case "system_role" -> "USER";
            case "profile_status" -> "PENDING";
            case "kyc_status" -> "NOT_STARTED";
            case "status" -> "beds".equals(table) || "rooms".equals(table) ? "AVAILABLE" : "ACTIVE";
            case "occupancy_status" -> "VACATED";
            case "role" -> "TENANT";
            case "type" -> "PG";
            case "room_type" -> "SHARED";
            case "enquiry_emails" -> "[]";
            default -> switch (dataType) {
                case "boolean" -> false;
                case "integer", "smallint", "bigint" -> 0;
                case "numeric" -> 0;
                case "date" -> LocalDate.of(2026, 11, 1);
                case "timestamp without time zone", "timestamp with time zone" -> Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 0, 0));
                case "jsonb", "json" -> "[]";
                default -> throw new IllegalStateException("Missing required " + table + "." + column + " (" + dataType + ")");
            };
        };
    }

    private static UUID insertCommitted(
            Fixture fixture,
            UUID memberId,
            OccupancyStatus status,
            LocalDate start,
            LocalDate end,
            LocalDateTime vacatedAt)
            throws Exception {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            UUID id = insertStay(connection, fixture, memberId, status, start, end, vacatedAt);
            connection.commit();
            return id;
        }
    }

    private static String code(String prefix) {
        return prefix + String.format("%05d", CODES.getAndIncrement());
    }

    private static String mobile() {
        return String.format("9%09d", CODES.getAndIncrement());
    }
}
