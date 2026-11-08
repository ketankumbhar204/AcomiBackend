package com.acomi.acomi_backend.occupancy.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Disposable PostgreSQL evidence for the release gate. Connects only to a new
 * {@code acomi_release_gate_} database and the maintenance database {@code postgres}.
 */
@EnabledIf("postgresAvailable")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReleaseGatePersistenceTest {

    private static final AtomicInteger CODES = new AtomicInteger(200);
    private static ReleaseGateIsolatedDatabase database;
    private static UUID occupancyId;
    private static UUID spaceId;
    private static UUID memberId;
    private static UUID userId;

    static boolean postgresAvailable() {
        return ReleaseGateIsolatedDatabase.postgresAvailable();
    }

    @BeforeAll
    static void openAtVersion155() throws Exception {
        assumeTrue(postgresAvailable());
        database = ReleaseGateIsolatedDatabase.open();
        System.out.println("RELEASE_GATE_DB=" + database.name);
        database.migrateTo("155");
        assertThat(columnExists("occupancies", "stay_kind")).isFalse();
        assertThat(columnExists("spaces", "short_stay_enabled")).isFalse();
        assertThat(currentDatabase()).isEqualTo(database.name);
    }

    @AfterAll
    static void dropDisposableDatabase() throws Exception {
        if (database != null) {
            database.close();
        }
    }

    @Test
    @Order(1)
    void upgradeAppliesV156V157AndV158() throws Exception {
        database.migrateLatest();
        assertThat(flywayVersion()).isEqualTo("158");
        assertThat(columnExists("occupancies", "stay_kind")).isTrue();
        assertThat(columnExists("occupancies", "pricing_basis")).isTrue();
        assertThat(columnExists("occupancies", "agreed_total")).isTrue();
        assertThat(columnExists("occupancies", "prior_rent_snapshot")).isTrue();
        assertThat(columnExists("occupancies", "rent_effective_month")).isTrue();
        assertThat(columnExists("occupancy_history", "idempotency_key")).isTrue();
        assertThat(columnExists("spaces", "short_stay_enabled")).isTrue();
        assertThat(indexExists("uq_space_payments_rent_extra_period")).isTrue();
        assertThat(indexExists("uq_occupancy_history_extension_key")).isTrue();
        assertThat(indexExists("uq_occupancies_member_active")).isTrue();
        assertThat(columnDefault("spaces", "short_stay_enabled")).contains("false");
    }

    @Test
    @Order(2)
    void persistedLedgerRowsMatchScenariosA_C_D_E() throws Exception {
        createFixture();
        occupancyId = insertShortStay();
        insertPayment("RENT", "EXTRA", "Short stay", 5600, "PENDING", "2026-11",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8));
        insertPayment("DEPOSIT", "SECURITY", "Deposit", 2000, "PENDING", "2026-11", null, null);

        assertThat(countPayments("RENT", "MONTHLY")).isZero();
        assertThat(sumAmounts()).isEqualByComparingTo("7600.00");
        assertThat(countPayments("RENT", "EXTRA")).isEqualTo(1);
        assertThat(countPayments("DEPOSIT", "SECURITY")).isEqualTo(1);

        markExtraPaid(5600, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8));
        insertPayment("RENT", "EXTRA", "Stay extension", 1600, "PENDING", "2026-11",
                LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10));
        assertThat(amountOf("RENT", "EXTRA", "PAID")).isEqualByComparingTo("5600.00");
        assertThat(amountOf("RENT", "EXTRA", "PENDING")).isEqualByComparingTo("1600.00");
        assertThat(countPayments("DEPOSIT", "SECURITY")).isEqualTo(1);
        assertThat(periodsShareANight()).isFalse();

        UUID monthlyMemberId = UUID.randomUUID();
        try (Connection connection = database.connect()) {
            insert(connection, "members", Map.of(
                    "id", monthlyMemberId,
                    "space_id", spaceId,
                    "full_name", "Monthly Member",
                    "mobile_number", mobile(),
                    "role", "TENANT",
                    "reference_code", code("MEM")));
        }
        UUID monthlyId = insertOccupancy(monthlyMemberId, null, null, null, 10000, LocalDate.of(2026, 11, 1));
        insertPaymentFor(monthlyMemberId, monthlyId, "RENT", "MONTHLY", "January rent", 10000, "PAID", "2026-01",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        insertPaymentFor(monthlyMemberId, monthlyId, "RENT", "MONTHLY", "February rent", 10000, "PENDING", "2026-02",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(countPaymentsFor(monthlyId, "RENT", "EXTRA")).isZero();

        insertPaymentFor(monthlyMemberId, monthlyId, "RENT", "MONTHLY", "October rent", 10000, "PAID", "2026-10",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        insertPaymentFor(monthlyMemberId, monthlyId, "RENT", "MONTHLY", "November rent", 11000, "PENDING", "2026-11",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertThat(amountForMonth(monthlyId, "2026-10")).isEqualByComparingTo("10000.00");
        assertThat(statusForMonth(monthlyId, "2026-10")).isEqualTo("PAID");
        assertThat(amountForMonth(monthlyId, "2026-11")).isEqualByComparingTo("11000.00");
        assertThat(statusForMonth(monthlyId, "2026-11")).isEqualTo("PENDING");

        assertThat(duplicateExtraRejected()).isTrue();
    }

    @Test
    @Order(3)
    void openNotificationDedupeAllowsOneRow() throws Exception {
        String key = "STAY_OVERDUE_ACTIVE:" + occupancyId + ":2026-11-08:" + userId;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> insertNotification(key));
            Future<Boolean> second = pool.submit(() -> insertNotification(key));
            int saved = 0;
            int conflicts = 0;
            for (Future<Boolean> future : java.util.List.of(first, second)) {
                try {
                    if (future.get()) {
                        saved++;
                    }
                } catch (ExecutionException ex) {
                    if (isUniqueViolation(ex)) {
                        conflicts++;
                    } else {
                        throw ex;
                    }
                }
            }
            assertThat(saved).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(countOpenNotifications(key)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(4)
    void manualRollbackRemovesStayColumnsAndKeepsTheOccupancy() throws Exception {
        try (Connection connection = database.connect();
                var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE spaces DROP COLUMN short_stay_enabled");
            statement.execute("DROP INDEX uq_space_payments_rent_extra_period");
            statement.execute("DROP INDEX uq_occupancy_history_extension_key");
            statement.execute("ALTER TABLE occupancy_history DROP COLUMN idempotency_key");
            statement.execute("ALTER TABLE occupancy_history DROP COLUMN extension_charge_id");
            statement.execute("ALTER TABLE occupancies DROP CONSTRAINT chk_occupancies_prior_rent_snapshot");
            statement.execute("ALTER TABLE occupancies DROP COLUMN prior_rent_snapshot");
            statement.execute("ALTER TABLE occupancies DROP COLUMN rent_effective_month");
            statement.execute("ALTER TABLE occupancy_history DROP CONSTRAINT chk_occupancy_history_event_type");
            statement.execute(
                    """
                    ALTER TABLE occupancy_history
                        ADD CONSTRAINT chk_occupancy_history_event_type
                        CHECK (event_type IN (
                            'ALLOCATED', 'TRANSFERRED', 'VACATED',
                            'RESERVED', 'MOVE_IN', 'RESERVATION_CANCELLED'
                        ))
                    """);
            statement.execute("ALTER TABLE occupancies DROP CONSTRAINT chk_occupancies_stay_kind");
            statement.execute("ALTER TABLE occupancies DROP CONSTRAINT chk_occupancies_agreed_total");
            statement.execute("ALTER TABLE occupancies DROP COLUMN stay_kind");
            statement.execute("ALTER TABLE occupancies DROP COLUMN pricing_basis");
            statement.execute("ALTER TABLE occupancies DROP COLUMN agreed_total");
        }
        assertThat(columnExists("occupancies", "stay_kind")).isFalse();
        assertThat(columnExists("spaces", "short_stay_enabled")).isFalse();
        assertThat(occupancyStatus()).isEqualTo("ACTIVE");
        assertThat(currentDatabase()).isNotEqualTo("acomi_db");
    }

    private static void createFixture() throws Exception {
        userId = UUID.randomUUID();
        spaceId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(true);
            insert(connection, "users", Map.of(
                    "id", userId,
                    "mobile_number", mobile(),
                    "full_name", "Gate User",
                    "reference_code", code("USR")));
            insert(connection, "spaces", Map.of(
                    "id", spaceId,
                    "owner_id", userId,
                    "name", "Gate Space",
                    "type", "PG",
                    "reference_code", code("SPC")));
            insert(connection, "members", Map.of(
                    "id", memberId,
                    "space_id", spaceId,
                    "full_name", "Gate Member",
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
                    "room_number", "1",
                    "room_type", "SHARED",
                    "reference_code", code("ROM")));
            insert(connection, "beds", Map.of(
                    "id", bedId,
                    "room_id", roomId,
                    "name", "Bed",
                    "bed_number", "1",
                    "reference_code", code("BED")));
        }
    }

    private static UUID insertShortStay() throws Exception {
        return insertOccupancy(memberId, "SHORT", "FIXED_TOTAL", 5600, null, null);
    }

    private static UUID insertOccupancy(
            UUID stayMemberId,
            String stayKind,
            String pricingBasis,
            Integer agreedTotal,
            Integer priorRent,
            LocalDate rentEffectiveMonth) throws Exception {
        UUID id = UUID.randomUUID();
        UUID buildingId = oneId("buildings");
        UUID floorId = oneId("floors");
        UUID roomId = oneId("rooms");
        UUID bedId = UUID.randomUUID();
        try (Connection connection = database.connect()) {
            insert(connection, "beds", Map.of(
                    "id", bedId,
                    "room_id", roomId,
                    "name", "Bed " + code("B"),
                    "bed_number", String.valueOf(CODES.getAndIncrement()),
                    "reference_code", code("BED")));
        }
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO occupancies (
                            id, reference_code, space_id, member_id, target_type, building_id, floor_id, room_id, bed_id,
                            allocated_at, allocated_by, move_in_date, expected_exit_date, expected_checkout_date,
                            status, stay_kind, pricing_basis, agreed_total, prior_rent_snapshot, rent_effective_month,
                            created_by, updated_by)
                        VALUES (?, ?, ?, ?, 'BED', ?, ?, ?, ?, NOW(), ?, DATE '2026-11-01', DATE '2026-11-08', DATE '2026-11-08',
                            'ACTIVE', ?, ?, ?, ?, ?, ?, ?)
                        """)) {
            statement.setObject(1, id);
            statement.setString(2, code("STY"));
            statement.setObject(3, spaceId);
            statement.setObject(4, stayMemberId);
            statement.setObject(5, buildingId);
            statement.setObject(6, floorId);
            statement.setObject(7, roomId);
            statement.setObject(8, bedId);
            statement.setObject(9, userId);
            statement.setString(10, stayKind);
            statement.setString(11, pricingBasis);
            if (agreedTotal == null) {
                statement.setObject(12, null);
            } else {
                statement.setBigDecimal(12, java.math.BigDecimal.valueOf(agreedTotal));
            }
            if (priorRent == null) {
                statement.setObject(13, null);
            } else {
                statement.setBigDecimal(13, java.math.BigDecimal.valueOf(priorRent));
            }
            statement.setObject(14, rentEffectiveMonth);
            statement.setObject(15, userId);
            statement.setObject(16, userId);
            statement.executeUpdate();
        }
        return id;
    }

    private static void insertPayment(
            String type, String category, String title, int amount, String status, String month,
            LocalDate start, LocalDate end) throws Exception {
        insertPaymentFor(memberId, occupancyId, type, category, title, amount, status, month, start, end);
    }

    private static void insertPaymentFor(
            UUID payMemberId, UUID stayId, String type, String category, String title, int amount, String status, String month,
            LocalDate start, LocalDate end) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO space_payments (
                            id, space_id, member_id, occupancy_id, payment_type, payment_category, title,
                            amount, currency_code, due_date, month, payment_status,
                            billing_period_start, billing_period_end)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'INR', ?, ?, ?, ?, ?)
                        """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, spaceId);
            statement.setObject(3, payMemberId);
            statement.setObject(4, stayId);
            statement.setString(5, type);
            statement.setString(6, category);
            statement.setString(7, title);
            statement.setBigDecimal(8, java.math.BigDecimal.valueOf(amount));
            statement.setObject(9, start == null ? LocalDate.of(2026, 11, 1) : start);
            statement.setString(10, month);
            statement.setString(11, status);
            statement.setObject(12, start);
            statement.setObject(13, end);
            statement.executeUpdate();
        }
    }

    private static void markExtraPaid(int amount, LocalDate start, LocalDate end) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        UPDATE space_payments
                        SET payment_status = 'PAID'
                        WHERE occupancy_id = ? AND payment_type = 'RENT' AND payment_category = 'EXTRA'
                          AND amount = ? AND billing_period_start = ? AND billing_period_end = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setBigDecimal(2, java.math.BigDecimal.valueOf(amount));
            statement.setObject(3, start);
            statement.setObject(4, end);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static boolean duplicateExtraRejected() throws Exception {
        try {
            insertPayment("RENT", "EXTRA", "Duplicate", 5600, "PENDING", "2026-11",
                    LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8));
            return false;
        } catch (SQLException ex) {
            return "23505".equals(ex.getSQLState());
        }
    }

    private static boolean insertNotification(String dedupeKey) throws Exception {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(true);
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("id", UUID.randomUUID());
            notification.put("space_id", spaceId);
            notification.put("user_id", userId);
            notification.put("entity_type", "OCCUPANCY");
            notification.put("entity_id", occupancyId);
            notification.put("notification_type", "STAY_OVERDUE_ACTIVE");
            notification.put("category", "ACTION_REQUIRED");
            notification.put("priority", "HIGH");
            notification.put("title", "Stay still active");
            notification.put("status", "UNREAD");
            notification.put("dedupe_key", dedupeKey);
            insert(connection, "space_notifications", notification);
            return true;
        }
    }

    private static boolean isUniqueViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
                    if (!row.containsKey(name)) {
                        row.put(name, fallback(table, name, columns.getString("data_type"), columns.getString("udt_name")));
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

    private static Object fallback(String table, String column, String dataType, String udt) {
        if ("uuid".equals(dataType) || "uuid".equals(udt)) {
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
            case "message" -> "Stay still active";
            case "delivery_channels" -> "IN_APP";
            default -> switch (dataType) {
                case "boolean" -> false;
                case "integer", "smallint", "bigint" -> 0;
                case "numeric" -> 0;
                case "date" -> LocalDate.of(2026, 11, 1);
                case "timestamp without time zone", "timestamp with time zone" ->
                        Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 0, 0));
                case "jsonb", "json" -> "[]";
                case "text", "character varying" -> "gate";
                default -> throw new IllegalStateException("Missing required " + table + "." + column + " (" + dataType + ")");
            };
        };
    }

    private static boolean columnExists(String table, String column) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static boolean indexExists(String name) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?")) {
            statement.setString(1, name);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static String flywayVersion() throws Exception {
        try (Connection connection = database.connect();
                var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static String columnDefault(String table, String column) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT column_default FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                        """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static String currentDatabase() throws Exception {
        try (Connection connection = database.connect();
                var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT current_database()")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static UUID oneId(String table) throws Exception {
        try (Connection connection = database.connect();
                var statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id FROM " + table + " LIMIT 1")) {
            assertThat(rows.next()).isTrue();
            return rows.getObject(1, UUID.class);
        }
    }

    private static int countPayments(String type, String category) throws Exception {
        return countPaymentsFor(occupancyId, type, category);
    }

    private static int countPaymentsFor(UUID stayId, String type, String category) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ?
                        """)) {
            statement.setObject(1, stayId);
            statement.setString(2, type);
            statement.setString(3, category);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static java.math.BigDecimal sumAmounts() throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(SUM(amount), 0) FROM space_payments WHERE occupancy_id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getBigDecimal(1);
            }
        }
    }

    private static java.math.BigDecimal amountOf(String type, String category, String status) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT amount FROM space_payments
                        WHERE occupancy_id = ? AND payment_type = ? AND payment_category = ? AND payment_status = ?
                        """)) {
            statement.setObject(1, occupancyId);
            statement.setString(2, type);
            statement.setString(3, category);
            statement.setString(4, status);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private static boolean periodsShareANight() throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT a.billing_period_start, a.billing_period_end, b.billing_period_start, b.billing_period_end
                        FROM space_payments a
                        JOIN space_payments b ON a.occupancy_id = b.occupancy_id AND a.id <> b.id
                        WHERE a.occupancy_id = ? AND a.payment_category = 'EXTRA' AND b.payment_category = 'EXTRA'
                          AND a.billing_period_start < b.billing_period_end
                          AND b.billing_period_start < a.billing_period_end
                        """)) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static java.math.BigDecimal amountForMonth(UUID stayId, String month) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT amount FROM space_payments
                        WHERE occupancy_id = ? AND month = ? AND payment_type = 'RENT' AND payment_category = 'MONTHLY'
                        """)) {
            statement.setObject(1, stayId);
            statement.setString(2, month);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBigDecimal(1);
            }
        }
    }

    private static String statusForMonth(UUID stayId, String month) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT payment_status FROM space_payments
                        WHERE occupancy_id = ? AND month = ? AND payment_type = 'RENT' AND payment_category = 'MONTHLY'
                        """)) {
            statement.setObject(1, stayId);
            statement.setString(2, month);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static int countOpenNotifications(String key) throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT COUNT(*) FROM space_notifications
                        WHERE dedupe_key = ? AND status IN ('UNREAD', 'READ')
                        """)) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static String occupancyStatus() throws Exception {
        try (Connection connection = database.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT status FROM occupancies WHERE id = ?")) {
            statement.setObject(1, occupancyId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    private static String code(String prefix) {
        return prefix + String.format("%05d", CODES.getAndIncrement());
    }

    private static String mobile() {
        return String.format("9%09d", CODES.getAndIncrement());
    }
}
