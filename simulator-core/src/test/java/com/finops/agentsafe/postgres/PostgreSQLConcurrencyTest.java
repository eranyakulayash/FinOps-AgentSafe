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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLConcurrencyTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private final String supervisorToken = "SUP-SECRET-AUTH-TOKEN-9988";
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Concurrency Merchant", new BigDecimal("1.50"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("Verify Concurrency Invariant: Two simultaneous $70 refunds against a $100 payment must never yield $140 total refunds")
    void testConcurrentRefundsOverdrawProtection() throws Exception {
        String paymentId = "CONCURRENCY-PAY-100";
        paymentService.processPayment(paymentId, "IDEMP-CONCURRENCY-PAY", merchant.getId(), new BigDecimal("100.00"), "USD");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);

        List<Future<Transaction>> futures = new ArrayList<>();
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();

        // Submit Refund A ($70.00)
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return paymentService.processRefund(
                    "REFUND-CONCURRENCY-A",
                    "IDEMP-CONCUR-A",
                    paymentId,
                    new BigDecimal("70.00"),
                    supervisorToken
                );
            } catch (Throwable t) {
                exceptions.add(t);
                throw t;
            } finally {
                endLatch.countDown();
            }
        }));

        // Submit Refund B ($70.00)
        futures.add(executor.submit(() -> {
            try {
                startLatch.await();
                return paymentService.processRefund(
                    "REFUND-CONCURRENCY-B",
                    "IDEMP-CONCUR-B",
                    paymentId,
                    new BigDecimal("70.00"),
                    supervisorToken
                );
            } catch (Throwable t) {
                exceptions.add(t);
                throw t;
            } finally {
                endLatch.countDown();
            }
        }));

        // Fire both concurrent requests simultaneously
        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify total refunded amount in PostgreSQL database
        BigDecimal totalRefundedInDb = transactionRepository.findTotalRefundedForPayment(paymentId);

        // Crucial invariant assertion: Total refunds MUST NEVER be $140.00
        assertNotEquals(new BigDecimal("140.00"), totalRefundedInDb, "INVARIANT VIOLATED: Concurrent refunds resulted in $140 total refunds!");
        assertEquals(new BigDecimal("70.00"), totalRefundedInDb, "Exactly one $70.00 refund must succeed in PostgreSQL");

        // At least one operation must have failed or been rejected
        assertFalse(exceptions.isEmpty(), "At least one concurrent refund operation must throw an exception");
    }
}
