package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.ReconciliationRecord;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.MatchStatus;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.ReconciliationRecordRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLConstraintAndLockingTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ReconciliationRecordRepository reconciliationRecordRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Constraint Merchant", new BigDecimal("2.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("Verify PostgreSQL unique constraints reject duplicate idempotency key and duplicate reconciliation record")
    void testUniqueConstraintViolation() {
        String txId1 = "TX-UNIQUE-1";
        String txId2 = "TX-UNIQUE-2";
        String sameIdempKey = "UNIQUE-KEY-EXPLICIT";

        paymentService.processPayment(txId1, sameIdempKey, merchant.getId(), new BigDecimal("50.00"), "USD");

        // Manually attempting to insert another transaction entity with the same idempotency key directly into DB
        Transaction duplicateKeyTx = new Transaction(
            txId2,
            sameIdempKey,
            merchant.getId(),
            new BigDecimal("75.00"),
            "USD",
            com.finops.agentsafe.enums.TransactionType.PAYMENT,
            TransactionStatus.SETTLED,
            null
        );

        assertThrows(Exception.class, () -> transactionRepository.saveAndFlush(duplicateKeyTx));

        // Test UNIQUE constraint on reconciliation_records.transaction_id
        ReconciliationRecord rec1 = new ReconciliationRecord(UUID.randomUUID(), txId1, null, BigDecimal.ZERO, MatchStatus.MATCHED);
        reconciliationRecordRepository.saveAndFlush(rec1);

        ReconciliationRecord rec2 = new ReconciliationRecord(UUID.randomUUID(), txId1, null, BigDecimal.ZERO, MatchStatus.MATCHED);
        assertThrows(Exception.class, () -> reconciliationRecordRepository.saveAndFlush(rec2));
    }

    @Test
    @DisplayName("Verify Optimistic Locking: competing updates on stale entity version trigger ObjectOptimisticLockingFailureException")
    void testOptimisticLockingOnTransaction() {
        String txId = "TX-OPT-LOCK-1";
        paymentService.processPayment(txId, "IDEMP-OPT-LOCK", merchant.getId(), new BigDecimal("100.00"), "USD");

        Transaction tx1 = transactionRepository.findById(txId).orElseThrow();
        Transaction tx2 = transactionRepository.findById(txId).orElseThrow();

        assertEquals(0L, tx1.getVersion());
        assertEquals(0L, tx2.getVersion());

        // Update tx1 and flush to increment database version to 1
        tx1.setStatus(TransactionStatus.RECONCILED);
        transactionRepository.saveAndFlush(tx1);

        // Attempting to update tx2 (still holds version 0) must throw optimistic locking failure
        tx2.setStatus(TransactionStatus.FAILED);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> transactionRepository.saveAndFlush(tx2));
    }

    @Test
    @DisplayName("Verify Transaction Rollback: database modification rolls back on unhandled exception")
    void testTransactionRollbackOnException() {
        String txId = "TX-ROLLBACK-1";

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> {
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.processPayment(txId, "IDEMP-ROLLBACK", merchant.getId(), new BigDecimal("120.00"), "USD");
                // Force artificial failure inside transaction block
                throw new RuntimeException("Forced artificial transactional failure");
            });
        });

        // Verify that txId was NOT persisted in PostgreSQL due to rollback
        assertTrue(transactionRepository.findById(txId).isEmpty(), "Transaction must not exist after rollback");
    }
}
