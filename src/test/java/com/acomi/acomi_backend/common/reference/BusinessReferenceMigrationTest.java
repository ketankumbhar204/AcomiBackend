package com.acomi.acomi_backend.common.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Applies the real Flyway history, including V151, to a disposable local database.
 * Does not touch {@code acomi_db}.
 */
class BusinessReferenceMigrationTest {

    private static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "Admin@123";
    private static final String DATABASE = "acomi_ref_mig_test";

    @Test
    @EnabledIf("postgresAvailable")
    void backfillAssignsStableCodesAndRejectsChanges() throws Exception {
        recreateDatabase();
        String url = "jdbc:postgresql://localhost:5432/" + DATABASE;
        Flyway.configure()
                .dataSource(url, USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO users (
                        id, mobile_number, full_name, enquiry_emails, profile_completed,
                        profile_status, profile_completion_percentage, documents_uploaded,
                        kyc_status, is_active, test_user, system_role, created_at, updated_at,
                        reference_code)
                    VALUES (
                        '%s', '9000000099', 'Later User', '[]'::jsonb, false,
                        'PENDING', 0, 0, 'NOT_STARTED', true, false, 'USER', NOW(), NOW(),
                        'USR99999')
                    """.formatted(UUID.randomUUID()));

            try (ResultSet codes = statement.executeQuery(
                    "SELECT reference_code FROM users ORDER BY created_at, id")) {
                assertThat(codes.next()).isTrue();
                String first = codes.getString(1);
                assertThat(first).startsWith("USR");
                assertThat(first.length()).isGreaterThanOrEqualTo(8);
                assertThat(codes.next()).isTrue();
                assertThat(codes.getString(1)).isEqualTo("USR99999");
            }

            try (ResultSet nulls = statement.executeQuery(
                    "SELECT COUNT(*) FROM users WHERE reference_code IS NULL")) {
                assertThat(nulls.next()).isTrue();
                assertThat(nulls.getInt(1)).isZero();
            }

            assertThatThrownBy(() -> statement.execute(
                    "UPDATE users SET reference_code = 'USR00002' WHERE mobile_number = '9000000099'"))
                    .hasMessageContaining("immutable");

            try (ResultSet next = statement.executeQuery("SELECT nextval('usr_reference_seq')")) {
                assertThat(next.next()).isTrue();
                assertThat(next.getLong(1)).isGreaterThan(1);
            }
        }
    }

    static boolean postgresAvailable() {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD)) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }

    private static void recreateDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                            + DATABASE
                            + "' AND pid <> pg_backend_pid()");
            statement.execute("DROP DATABASE IF EXISTS " + DATABASE);
            statement.execute("CREATE DATABASE " + DATABASE);
        }
    }
}
