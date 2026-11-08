package com.acomi.acomi_backend.occupancy.application.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;

/**
 * A database created for one test run. It is never {@code acomi_db}.
 * Flyway runs only against this database, then the database is dropped.
 */
final class Phase5IsolatedDatabase implements AutoCloseable {

    static final String ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    static final String USER = "postgres";
    static final String PASSWORD = "Admin@123";

    final String name;
    final String url;

    private Phase5IsolatedDatabase(String name) {
        this.name = name;
        this.url = "jdbc:postgresql://localhost:5432/" + name;
    }

    static Phase5IsolatedDatabase open() throws Exception {
        String name = "acomi_phase5_race_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        if (!name.matches("acomi_phase5_race_[0-9a-f]{12}") || "acomi_db".equals(name)) {
            throw new IllegalStateException("Refusing database name " + name);
        }
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                Statement statement = admin.createStatement()) {
            try (ResultSet existing = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + name + "'")) {
                if (existing.next()) {
                    throw new IllegalStateException("Refusing to reuse existing database " + name);
                }
            }
            statement.execute("CREATE DATABASE " + name);
        }
        Phase5IsolatedDatabase database = new Phase5IsolatedDatabase(name);
        try (Connection check = database.connect()) {
            if (!name.equals(check.getCatalog()) || "acomi_db".equals(check.getCatalog())) {
                throw new IllegalStateException("Connected to " + check.getCatalog() + " instead of " + name);
            }
        }
        Flyway.configure()
                .dataSource(database.url, USER, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
        try (Connection check = database.connect();
                Statement statement = check.createStatement();
                ResultSet current = statement.executeQuery("SELECT current_database()")) {
            current.next();
            if (!name.equals(current.getString(1))) {
                throw new IllegalStateException("Migration ran on " + current.getString(1));
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

    boolean sharedDatabaseStillPresent() throws Exception {
        try (Connection admin = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
                Statement statement = admin.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT 1 FROM pg_database WHERE datname = 'acomi_db'")) {
            return result.next();
        }
    }

    @Override
    public void close() throws Exception {
        if (!name.matches("acomi_phase5_race_[0-9a-f]{12}")) {
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
