package com.acomi.acomi_backend.occupancy.application.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;

/**
 * One disposable database for the release-gate check. Never {@code acomi_db}.
 */
final class ReleaseGateIsolatedDatabase implements AutoCloseable {

    static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    static final String USER = "postgres";
    static final String PASSWORD = "Admin@123";

    final String name;
    final String url;

    private ReleaseGateIsolatedDatabase(String name) {
        this.name = name;
        this.url = "jdbc:postgresql://localhost:5432/" + name;
    }

    static ReleaseGateIsolatedDatabase open() throws Exception {
        String name = "acomi_release_gate_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (!name.matches("acomi_release_gate_[0-9a-f]{12}") || "acomi_db".equals(name)) {
            throw new IllegalStateException("Refusing database name " + name);
        }
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                Statement statement = admin.createStatement()) {
            try (ResultSet existing = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
                if (existing.next()) {
                    throw new IllegalStateException("Refusing to reuse " + name);
                }
            }
            statement.execute("CREATE DATABASE " + name);
        }
        ReleaseGateIsolatedDatabase database = new ReleaseGateIsolatedDatabase(name);
        try (Connection check = database.connect()) {
            if (!name.equals(check.getCatalog()) || "acomi_db".equals(check.getCatalog())) {
                throw new IllegalStateException("Connected to " + check.getCatalog());
            }
        }
        return database;
    }

    static boolean postgresAvailable() {
        try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD)) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }

    void migrateTo(String target) {
        Flyway.configure()
                .dataSource(url, USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .target(target)
                .load()
                .migrate();
    }

    void migrateLatest() {
        Flyway.configure()
                .dataSource(url, USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    Connection connect() throws Exception {
        if (url.contains("/acomi_db")) {
            throw new IllegalStateException("Refusing connection to acomi_db");
        }
        Connection connection = DriverManager.getConnection(url, USER, PASSWORD);
        if (!name.equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalStateException("Refusing connection to " + connection.getCatalog());
        }
        return connection;
    }

    @Override
    public void close() throws Exception {
        if (!name.matches("acomi_release_gate_[0-9a-f]{12}")) {
            throw new IllegalStateException("Refusing to drop " + name);
        }
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                Statement statement = admin.createStatement()) {
            statement.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '"
                            + name
                            + "' AND pid <> pg_backend_pid()");
            statement.execute("DROP DATABASE IF EXISTS " + name);
            try (ResultSet gone = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
                if (gone.next()) {
                    throw new IllegalStateException("Disposable database was not dropped: " + name);
                }
            }
            try (ResultSet shared = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = 'acomi_db'")) {
                if (!shared.next()) {
                    throw new IllegalStateException("acomi_db is missing after disposable cleanup");
                }
            }
        }
    }
}
