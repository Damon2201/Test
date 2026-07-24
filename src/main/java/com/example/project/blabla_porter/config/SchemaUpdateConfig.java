package com.example.project.blabla_porter.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

@Component
public class SchemaUpdateConfig {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void dropNotNullConstraints() {
        try {
            // Drop NOT NULL constraints from payments columns in PostgreSQL / H2
            jdbcTemplate.execute("ALTER TABLE payments ALTER COLUMN parcel_request_id DROP NOT NULL");
            jdbcTemplate.execute("ALTER TABLE payments ALTER COLUMN sender_id DROP NOT NULL");
            System.out.println("=== Database Schema Altered: dropped NOT NULL constraints from payments columns successfully ===");
        } catch (Exception e) {
            System.out.println("=== Database Schema Alteration Alert: " + e.getMessage() + " ===");
        }
    }
}
