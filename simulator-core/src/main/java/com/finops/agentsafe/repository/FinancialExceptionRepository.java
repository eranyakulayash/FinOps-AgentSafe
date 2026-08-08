package com.finops.agentsafe.repository;

import com.finops.agentsafe.domain.FinancialException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FinancialExceptionRepository extends JpaRepository<FinancialException, UUID> {
    List<FinancialException> findByTransactionId(String transactionId);
    List<FinancialException> findByBatchId(UUID batchId);
}
