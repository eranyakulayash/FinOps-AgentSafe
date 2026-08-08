package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLSchemaAndFlywayTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Verify all required tables exist in PostgreSQL after Flyway migration")
    void testFlywaySchemaMigrationTablesExist() {
        List<String> tables = jdbcTemplate.query(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            (rs, rowNum) -> rs.getString("table_name")
        );

        assertTrue(tables.contains("merchants"), "merchants table missing");
        assertTrue(tables.contains("transactions"), "transactions table missing");
        assertTrue(tables.contains("settlement_batches"), "settlement_batches table missing");
        assertTrue(tables.contains("settlement_line_items"), "settlement_line_items table missing");
        assertTrue(tables.contains("reconciliation_records"), "reconciliation_records table missing");
        assertTrue(tables.contains("financial_exceptions"), "financial_exceptions table missing");
        assertTrue(tables.contains("audit_events"), "audit_events table missing");
        assertTrue(tables.contains("human_approval_requests"), "human_approval_requests table missing");
        assertTrue(tables.contains("flyway_schema_history"), "flyway_schema_history table missing");
    }

    @Test
    @DisplayName("Verify PostgreSQL CHECK constraints are enforced by the database")
    void testFlywayCheckConstraintsEnforced() {
        // Attempting to insert a merchant with invalid fee rate percentage (> 100.00)
        String invalidMerchantSql = "INSERT INTO merchants (id, name, fee_rate_percentage, status, created_at) " +
                "VALUES ('00000000-0000-0000-0000-000000000001', 'Bad Merchant', 105.00, 'ACTIVE', CURRENT_TIMESTAMP)";

        Exception exception = assertThrows(Exception.class, () -> jdbcTemplate.execute(invalidMerchantSql));
        assertTrue(exception.getMessage().toLowerCase().contains("check constraint") 
                || exception.getMessage().toLowerCase().contains("violates check constraint"),
                "Expected PostgreSQL CHECK constraint failure");
    }
}
