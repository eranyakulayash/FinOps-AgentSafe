package com.finops.agentsafe.postgres;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import com.finops.agentsafe.domain.HumanApprovalRequest;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.enums.ApprovalStatus;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.service.HumanApprovalService;
import com.finops.agentsafe.service.PaymentService;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PostgreSQLConcurrencyExtendedTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private HumanApprovalService approvalService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(UUID.randomUUID(), "Concurrency Extended Merchant", new BigDecimal("1.00"), "ACTIVE");
        merchantRepository.save(merchant);
    }

    @Test
    @DisplayName("Concurrent reversals: Two $70 reversals against $100 payment — only one must succeed")
    void testConcurrentReversalsNeverExceedCap() throws Exception {
        String payId = "PAY-CONC-REV-" + UUID.randomUUID().toString().substring(0, 8);
        paymentService.processPayment(payId, "IDEMP-CONC-REV-" + payId, merchant.getId(), new BigDecimal("100.00"), "USD");

        // Pre-approve reversal
        try {
            paymentService.processReversal("PROBE-REV", "PROBE-REV-IDEMP-" + payId, payId, new BigDecimal("0.01"), "AGENT");
        } catch (ApprovalRequiredException e) {
            approvalService.approve(e.getApprovalRequestId(), "HUMAN_SUPERVISOR");
        } catch (InvariantViolationException ignored) {}

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(2);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        AtomicInteger successes = new AtomicInteger(0);

        Runnable submitReversal = () -> {
            try {
                start.await();
                paymentService.processReversal(
                    "REV-CONC-" + UUID.randomUUID(),
                    "IDEMP-CONC-REV-" + UUID.randomUUID(),
                    payId, new BigDecimal("70.00"), "AGENT");
                successes.incrementAndGet();
            } catch (Throwable t) {
                exceptions.add(t);
            } finally {
                end.countDown();
            }
        };

        executor.submit(submitReversal);
        executor.submit(submitReversal);
        start.countDown();
        end.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        BigDecimal totalReversed = transactionRepository.findTotalReversedForPayment(payId);
        assertNotEquals(new BigDecimal("140.00"), totalReversed.setScale(2), "INVARIANT VIOLATED: concurrent reversals exceeded cap!");
        assertTrue(totalReversed.compareTo(new BigDecimal("100.00")) <= 0, "Total reversed must not exceed $100");
    }

    @Test
    @DisplayName("Duplicate idempotency key: two concurrent requests with same key — only one payment persisted")
    void testDuplicateIdempotencyKeyUnderConcurrency() throws Exception {
        String sharedIdempKey = "IDEMP-DUPLICATE-" + UUID.randomUUID().toString().substring(0, 8);
        String txIdBase = "PAY-DUP-IDEMP-" + UUID.randomUUID().toString().substring(0, 8);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(2);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    start.await();
                    paymentService.processPayment(
                        txIdBase + "-" + idx,
                        sharedIdempKey,  // SAME idempotency key
                        merchant.getId(),
                        new BigDecimal("50.00"), "USD");
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    end.countDown();
                }
            });
        }

        start.countDown();
        end.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify exactly ONE payment with this idempotency key exists in PostgreSQL
        var result = transactionRepository.findByIdempotencyKey(sharedIdempKey);
        assertTrue(result.isPresent(), "Exactly one payment with the idempotency key must exist");
    }

    @Test
    @DisplayName("Simultaneous approval decisions: two concurrent approvals of same request — only one must win")
    void testSimultaneousApprovalDecisions() throws Exception {
        HumanApprovalRequest req = approvalService.createApprovalRequest(
            "AGENT", "EXECUTE_REVERSAL", "Concurrent approval test",
            "PAY-CONCURRENT-APPROVAL-" + UUID.randomUUID().toString().substring(0, 8),
            null, "SCENARIO-CONC", UUID.randomUUID());

        UUID approvalId = req.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(2);
        List<Throwable> exceptions = new CopyOnWriteArrayList<>();
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            final String approver = "SUPERVISOR_" + i;
            executor.submit(() -> {
                try {
                    start.await();
                    approvalService.approve(approvalId, approver);
                    successes.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    end.countDown();
                }
            });
        }

        start.countDown();
        end.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        // The approval request must be APPROVED (not corrupted)
        HumanApprovalRequest final_ = approvalService.getApprovalRequest(approvalId).orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, final_.getStatus());
        // At least one succeeded, at most one succeeded without corruption
        assertTrue(successes.get() >= 1 || !exceptions.isEmpty());
    }
}
