package com.finops.agentsafe.service;

import com.finops.agentsafe.clock.SystemSimulatorClock;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.identifier.RandomIdentifierGenerator;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.validator.FinancialInvariantValidator;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private HumanApprovalRequestRepository approvalRepository;

    private FinancialInvariantValidator invariantValidator;
    private PaymentService paymentService;

    private final String supervisorToken = "SUP-SECRET-AUTH-TOKEN-9988";

    @BeforeEach
    void setUp() {
        invariantValidator = new FinancialInvariantValidator();
        paymentService = new PaymentService(
            transactionRepository,
            merchantRepository,
            invariantValidator,
            auditService,
            new SystemSimulatorClock(),
            new RandomIdentifierGenerator(),
            approvalRepository
        );
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "supervisorToken", supervisorToken);
    }

    @Test
    @DisplayName("Should process valid payment transaction and record audit event")
    void testProcessPaymentSuccess() {
        UUID merchantId = UUID.randomUUID();
        Merchant merchant = new Merchant(merchantId, "Acme Store", new BigDecimal("2.50"), "ACTIVE");

        when(transactionRepository.findByIdempotencyKey("IDEMP-TX-1")).thenReturn(Optional.empty());
        when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        Transaction tx = paymentService.processPayment("TX-1", "IDEMP-TX-1", merchantId, new BigDecimal("150.00"), "USD");

        assertNotNull(tx);
        assertEquals("TX-1", tx.getId());
        assertEquals(new BigDecimal("150.00"), tx.getAmount());
        verify(auditService, times(1)).recordAuditEvent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject over-refunding attempts exceeding original payment amount")
    void testRefundExceedingPaymentAmount() {
        String paymentId = "TX-PAY-100";
        Transaction originalPayment = new Transaction(paymentId, "IDEMP-P1", UUID.randomUUID(), new BigDecimal("100.00"), "USD", com.finops.agentsafe.enums.TransactionType.PAYMENT, com.finops.agentsafe.enums.TransactionStatus.SETTLED, null);

        when(transactionRepository.findByIdempotencyKey("IDEMP-R1")).thenReturn(Optional.empty());
        when(transactionRepository.findById(paymentId)).thenReturn(Optional.of(originalPayment));
        when(transactionRepository.findTotalRefundedForPayment(paymentId)).thenReturn(new BigDecimal("80.00"));

        // Attempting to refund $30 when $80 was already refunded on a $100 payment
        assertThrows(InvariantViolationException.class, () -> 
            paymentService.processRefund("REFUND-1", "IDEMP-R1", paymentId, new BigDecimal("30.00"), supervisorToken)
        );
    }
}
