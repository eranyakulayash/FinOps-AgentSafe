package com.finops.agentsafe.service;

import com.finops.agentsafe.domain.Chargeback;
import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.SettlementBatch;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.repository.ChargebackRepository;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.SettlementBatchRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyntheticDataReproducibilityTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private SettlementBatchRepository batchRepository;

    @Mock
    private ChargebackRepository chargebackRepository;

    private SyntheticDataService syntheticDataService;

    @BeforeEach
    void setUp() {
        syntheticDataService = new SyntheticDataService(
            merchantRepository,
            transactionRepository,
            batchRepository,
            chargebackRepository
        );
    }

    @Test
    @DisplayName("Identical seed and generatorVersion produce identical synthetic datasets")
    void testSyntheticScenarioReproducibility() {
        long seed = 12345L;
        String genVersion = "1.0";

        // First run
        Merchant m1 = syntheticDataService.seedSyntheticScenario(seed, genVersion, "Test Merchant", 10);

        ArgumentCaptor<Transaction> txCaptor1 = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(13)).save(txCaptor1.capture()); // 10 payments + 2 refunds + 1 reversal
        List<Transaction> txsRun1 = txCaptor1.getAllValues();

        ArgumentCaptor<Chargeback> cbCaptor1 = ArgumentCaptor.forClass(Chargeback.class);
        verify(chargebackRepository, times(1)).save(cbCaptor1.capture());
        Chargeback cbRun1 = cbCaptor1.getValue();

        ArgumentCaptor<SettlementBatch> batchCaptor1 = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(batchRepository).save(batchCaptor1.capture());
        SettlementBatch batchRun1 = batchCaptor1.getValue();

        // Reset mocks for second run
        reset(merchantRepository, transactionRepository, batchRepository, chargebackRepository);

        // Second run with identical parameters
        Merchant m2 = syntheticDataService.seedSyntheticScenario(seed, genVersion, "Test Merchant", 10);

        ArgumentCaptor<Transaction> txCaptor2 = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, times(13)).save(txCaptor2.capture());
        List<Transaction> txsRun2 = txCaptor2.getAllValues();

        ArgumentCaptor<Chargeback> cbCaptor2 = ArgumentCaptor.forClass(Chargeback.class);
        verify(chargebackRepository, times(1)).save(cbCaptor2.capture());
        Chargeback cbRun2 = cbCaptor2.getValue();

        ArgumentCaptor<SettlementBatch> batchCaptor2 = ArgumentCaptor.forClass(SettlementBatch.class);
        verify(batchRepository).save(batchCaptor2.capture());
        SettlementBatch batchRun2 = batchCaptor2.getValue();

        // Assert reproducibility
        assertEquals(m1.getId(), m2.getId(), "Merchant IDs must be identical");
        assertEquals(txsRun1.size(), txsRun2.size(), "Transaction counts must be identical");

        for (int i = 0; i < txsRun1.size(); i++) {
            Transaction t1 = txsRun1.get(i);
            Transaction t2 = txsRun2.get(i);
            assertEquals(t1.getId(), t2.getId(), "Transaction ID at index " + i + " must match");
            assertEquals(t1.getAmount(), t2.getAmount(), "Amount at index " + i + " must match");
            assertEquals(t1.getIdempotencyKey(), t2.getIdempotencyKey(), "Idempotency key at index " + i + " must match");
            assertEquals(t1.getCreatedAt(), t2.getCreatedAt(), "Created timestamp at index " + i + " must match");
        }

        assertEquals(cbRun1.getId(), cbRun2.getId(), "Chargeback IDs must be identical");
        assertEquals(cbRun1.getIdempotencyKey(), cbRun2.getIdempotencyKey(), "Chargeback idempotency key must be identical");

        assertEquals(batchRun1.getId(), batchRun2.getId(), "Batch IDs must be identical");
        assertEquals(batchRun1.getTotalGrossAmount(), batchRun2.getTotalGrossAmount(), "Total gross must be identical");
        assertEquals(batchRun1.getTotalFeeAmount(), batchRun2.getTotalFeeAmount(), "Total fee must be identical");
        assertEquals(batchRun1.getTotalNetAmount(), batchRun2.getTotalNetAmount(), "Total net must be identical");
    }

    @Test
    @DisplayName("Different seeds produce different synthetic datasets")
    void testDifferentSeedsProduceDifferentDatasets() {
        String genVersion = "1.0";

        Merchant m1 = syntheticDataService.seedSyntheticScenario(100L, genVersion, "Merchant A", 5);
        Merchant m2 = syntheticDataService.seedSyntheticScenario(200L, genVersion, "Merchant A", 5);

        assertNotEquals(m1.getId(), m2.getId(), "Different seeds must produce different Merchant IDs");
    }
}
