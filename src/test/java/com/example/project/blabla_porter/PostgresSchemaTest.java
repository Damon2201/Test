package com.example.project.blabla_porter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.example.project.Application.class)
@TestPropertySource(properties = {
    "blabla.seeder.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:postgresql://localhost:5432/blabla_porter",
    "spring.datasource.username=blabla",
    "spring.datasource.password=blabla_secret",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.datasource.hikari.connection-timeout=600000",
    "spring.datasource.hikari.validation-timeout=600000",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
public class PostgresSchemaTest {

    static {
        System.setProperty("user.timezone", "UTC");
    }

    @Autowired
    private DataSource dataSource;

    @Test
    public void testSchemaCreationAndConstraints() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Check if trips table exists and has the constraint
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
