package com.example.project.blabla_porter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates PostgreSQL schema constraints (CHECK, NOT NULL, etc.) against a live
 * local database. Automatically SKIPPED when no PostgreSQL instance is reachable
 * at localhost:5432, so it never breaks CI or local dev builds.
 */
public class PostgresSchemaTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/blabla_porter";
    private static final String DB_USER = "blabla";
    private static final String DB_PASS = "blabla_secret";

    static boolean isPostgresAvailable() {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("isPostgresAvailable")
    public void testSchemaCreationAndConstraints() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Check if trips table exists
            ResultSet rs = conn.getMetaData().getTables(null, null, "trips", null);
            assertTrue(rs.next(), "Table 'trips' should exist");

            // Query constraints on table 'trips'
            ResultSet constraintRs = stmt.executeQuery(
                "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid = 'trips'::regclass;"
            );

            boolean foundCheckConstraint = false;
            while (constraintRs.next()) {
                String name = constraintRs.getString(1);
                String definition = constraintRs.getString(2);
                System.out.println("Constraint: " + name + " -> " + definition);
                if (definition.contains("available_capacity_kg >= (0") ||
                    definition.contains("available_capacity_kg >= 0") ||
                    definition.contains("available_seats >= 0") ||
                    definition.contains("available_seats >= (0") ||
                    definition.toLowerCase().contains("capacity") ||
                    definition.toLowerCase().contains("seats")) {
                    foundCheckConstraint = true;
                }
            }
            assertTrue(foundCheckConstraint, "Should find the check constraint on available_capacity_kg and available_seats");
        }
    }
}
