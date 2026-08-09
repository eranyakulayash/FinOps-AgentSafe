package com.finops.agentsafe;

import com.finops.agentsafe.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all PostgreSQL Testcontainers integration tests.
 *
 * <h3>Shared Container Strategy</h3>
 * A single static {@link PostgreSQLContainer} is started once for the entire test-suite
 * JVM run. Spring reuses the same {@code ApplicationContext} across all subclasses.
 * This gives fast test execution without per-class container restarts.
 *
 * <h3>Test Isolation</h3>
 * Because all tests share the same schema, data written by one test is visible to
 * subsequent tests unless it is cleaned up. The {@link #cleanDatabase()} method runs
 * {@code @BeforeEach} and truncates all tables in FK-safe (reverse-dependency) order:
 * <pre>
 *   reconciliation_records  → FK → transactions
 *   financial_exceptions    → no FK
 *   chargebacks             → FK → transactions
 *   human_approval_requests → no FK to transactions (but clears approval state)
 *   audit_events            → no FK
 *   settlement_line_items   → FK → settlement_batches (CASCADE)
 *   settlement_batches      → FK → merchants
 *   transactions            → FK → merchants
 *   merchants               → root table
 * </pre>
 * This guarantees every test starts with a clean database regardless of execution order.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgreSQLIntegrationTest {

    @ServiceConnection
    public static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("finops_test_db")
            .withUsername("test_user")
            .withPassword("test_pass");

    static {
        postgres.start();
    }

    // -------------------------------------------------------------------------
    // Repositories for FK-safe cleanup — injected by Spring for each subclass
    // -------------------------------------------------------------------------

    @Autowired
    private ReconciliationRecordRepository reconciliationRecordRepository;

    @Autowired
    private FinancialExceptionRepository financialExceptionRepository;

    @Autowired
    private ChargebackRepository chargebackRepository;

    @Autowired
    private HumanApprovalRequestRepository humanApprovalRequestRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private SettlementLineItemRepository settlementLineItemRepository;

    @Autowired
    private SettlementBatchRepository settlementBatchRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    /**
     * Truncates all tables before every test in FK-safe order.
     * Children are deleted before parents to avoid FK constraint violations.
     */
    @BeforeEach
    void cleanDatabase() {
        reconciliationRecordRepository.deleteAll();
        financialExceptionRepository.deleteAll();
        chargebackRepository.deleteAll();
        humanApprovalRequestRepository.deleteAll();
        auditEventRepository.deleteAll();
        settlementLineItemRepository.deleteAll();  // cascade deletes line items when batch deleted
        settlementBatchRepository.deleteAll();
        transactionRepository.deleteAll();
        merchantRepository.deleteAll();
    }
}

