package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.Chargeback;
import com.finops.agentsafe.enums.ChargebackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargebackRepository extends JpaRepository<Chargeback, UUID> {

    Optional<Chargeback> findByIdempotencyKey(String idempotencyKey);

    List<Chargeback> findByOriginalTransactionId(String originalTransactionId);

    List<Chargeback> findByOriginalTransactionIdAndStatus(String originalTransactionId, ChargebackStatus status);

    @Query("SELECT COALESCE(SUM(c.amount), 0.00) FROM Chargeback c WHERE c.originalTransactionId = :txId AND c.status NOT IN ('CLOSED')")
    BigDecimal findTotalActiveChargebackAmountForTransaction(@Param("txId") String transactionId);
}
