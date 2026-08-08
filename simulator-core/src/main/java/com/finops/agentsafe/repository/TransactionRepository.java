package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByMerchantId(UUID merchantId);

    List<Transaction> findByOriginalPaymentIdAndType(String originalPaymentId, TransactionType type);

    @Query("SELECT COALESCE(SUM(t.amount), 0.00) FROM Transaction t WHERE t.originalPaymentId = :paymentId AND t.type = 'REFUND' AND t.status != 'FAILED'")
    BigDecimal findTotalRefundedForPayment(@Param("paymentId") String paymentId);

    @Query("SELECT COALESCE(SUM(t.amount), 0.00) FROM Transaction t WHERE t.originalPaymentId = :paymentId AND t.type = 'REVERSAL' AND t.status != 'FAILED'")
    BigDecimal findTotalReversedForPayment(@Param("paymentId") String paymentId);
}

