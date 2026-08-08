package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLPaymentIntegrationTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Merchant testMerchant;

    @BeforeEach
    void setUp() {
        testMerchant = new Merchant(UUID.randomUUID(), "Test Store PG", new BigDecimal("2.50"), "ACTIVE");
        merchantRepository.save(testMerchant);
    }

    @Test
    @DisplayName("Verify payment creation and persistence in PostgreSQL")
    void testPaymentCreationAndPersistence() {
        String txId = "TX-PG-001";
        String idempKey = "IDEMP-PG-001";
        BigDecimal amount = new BigDecimal("150.75");

        Transaction tx = paymentService.processPayment(txId, idempKey, testMerchant.getId(), amount, "USD");

        assertNotNull(tx);
        assertEquals(txId, tx.getId());
        assertEquals(idempKey, tx.getIdempotencyKey());
        assertEquals(amount, tx.getAmount());

        Optional<Transaction> fetched = transactionRepository.findById(txId);
        assertTrue(fetched.isPresent());
        assertEquals(amount, fetched.get().getAmount());
    }

    @Test
    @DisplayName("Verify Idempotency: submitting same idempotency key twice returns original transaction")
    void testPaymentIdempotency() {
        String txId1 = "TX-IDEMP-1";
        String txId2 = "TX-IDEMP-2-DUPLICATE";
        String idempKey = "SHARED-IDEMP-KEY-999";
        BigDecimal amount = new BigDecimal("200.00");

        Transaction firstCall = paymentService.processPayment(txId1, idempKey, testMerchant.getId(), amount, "USD");
        Transaction secondCall = paymentService.processPayment(txId2, idempKey, testMerchant.getId(), amount, "USD");

        assertEquals(firstCall.getId(), secondCall.getId(), "Second call must return existing transaction instance");
        assertEquals(txId1, secondCall.getId());
        assertEquals(1, transactionRepository.findByMerchantId(testMerchant.getId()).stream()
                .filter(t -> idempKey.equals(t.getIdempotencyKey())).count(), "Duplicate record must not be created");
    }

    @Test
    @DisplayName("Verify Monetary Precision: exact persistence of 100.10, 0.01, and 999999.99 without precision loss")
    void testMonetaryPrecisionHandling() {
        BigDecimal[] testAmounts = {
            new BigDecimal("100.10"),
            new BigDecimal("0.01"),
            new BigDecimal("999999.99")
        };

        for (int i = 0; i < testAmounts.length; i++) {
            BigDecimal expected = testAmounts[i];
            String txId = "TX-PRECISION-" + i;
            String idempKey = "IDEMP-PRECISION-" + i;

            paymentService.processPayment(txId, idempKey, testMerchant.getId(), expected, "USD");

            Transaction retrieved = transactionRepository.findById(txId).orElseThrow();
            assertEquals(expected, retrieved.getAmount(), "Retrieved amount scale and value must match exactly");
        }
    }
}
