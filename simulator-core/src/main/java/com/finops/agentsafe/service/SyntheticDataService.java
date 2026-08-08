package com.finops.agentsafe.service;

import com.finops.agentsafe.domain.*;
import com.finops.agentsafe.enums.*;
import com.finops.agentsafe.identifier.SeededIdentifierGenerator;
import com.finops.agentsafe.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

/**
 * Generates deterministic synthetic benchmark scenario datasets.
 *
 * Given the same (seed, generatorVersion) inputs, this service produces
 * identical sets of Merchants, Payments, Refunds, Reversals, Chargebacks,
 * and SettlementBatches across benchmark runs.
 */
@Service
public class SyntheticDataService {

    public static final String DEFAULT_GENERATOR_VERSION = "1.0";

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final SettlementBatchRepository batchRepository;
    private final ChargebackRepository chargebackRepository;

    public SyntheticDataService(MerchantRepository merchantRepository,
                                TransactionRepository transactionRepository,
                                SettlementBatchRepository batchRepository,
                                ChargebackRepository chargebackRepository) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.chargebackRepository = chargebackRepository;
    }

    @Transactional
    public Merchant seedSyntheticScenario(long seed, String merchantName, int transactionCount) {
        return seedSyntheticScenario(seed, DEFAULT_GENERATOR_VERSION, merchantName, transactionCount);
    }

    @Transactional
    public Merchant seedSyntheticScenario(long seed, String generatorVersion, String merchantName, int transactionCount) {
        Random rng = new Random(seed);
        SeededIdentifierGenerator idGen = new SeededIdentifierGenerator(seed);

        String scenarioId = "SCENARIO-" + seed;
        UUID runId = UUID.nameUUIDFromBytes(("RUN-" + seed + "-" + generatorVersion).getBytes());
        Instant baseTime = Instant.ofEpochMilli(1735689600000L); // Fixed epoch 2025-01-01T00:00:00Z

        UUID merchantId = UUID.nameUUIDFromBytes(("MCH-" + seed + "-" + generatorVersion).getBytes());
        BigDecimal feeRate = BigDecimal.valueOf(2.50).setScale(2, RoundingMode.HALF_UP);
        Merchant merchant = new Merchant(merchantId, merchantName, feeRate, "ACTIVE");
        merchantRepository.save(merchant);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;

        UUID batchId = UUID.nameUUIDFromBytes(("STL-" + seed + "-" + generatorVersion).getBytes());
        SettlementBatch batch = new SettlementBatch(
            batchId,
            merchantId,
            "settlement_batch_" + seed + "_v" + generatorVersion + ".csv",
            "Deterministic synthetic settlement batch for scenario " + seed + " (generatorVersion=" + generatorVersion + ")",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            SettlementStatus.UNPROCESSED
        );

        for (int i = 1; i <= transactionCount; i++) {
            String txId = String.format("TX-%d-%s-%04d", seed, generatorVersion.replace(".", "_"), i);
            String idempotencyKey = "IDEMP-" + txId;
            BigDecimal amount = BigDecimal.valueOf(50 + rng.nextInt(450)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = amount.multiply(feeRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal net = amount.subtract(fee);

            Instant txTime = baseTime.plusSeconds(i * 60L);

            Transaction tx = new Transaction(
                txId,
                idempotencyKey,
                merchantId,
                amount,
                "USD",
                TransactionType.PAYMENT,
                TransactionStatus.SETTLED,
                null,
                scenarioId,
                runId,
                txTime
            );
            transactionRepository.save(tx);

            // Periodically generate a refund, reversal, or chargeback for complete lifecycle coverage
            if (i % 5 == 0) {
                String refundId = "REF-" + txId;
                BigDecimal refundAmt = amount.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                Transaction refundTx = new Transaction(
                    refundId,
                    "IDEMP-" + refundId,
                    merchantId,
                    refundAmt,
                    "USD",
                    TransactionType.REFUND,
                    TransactionStatus.SETTLED,
                    txId,
                    scenarioId,
                    runId,
                    txTime.plusSeconds(30)
                );
                transactionRepository.save(refundTx);
            }

            if (i % 7 == 0) {
                String reversalId = "REV-" + txId;
                Transaction reversalTx = new Transaction(
                    reversalId,
                    "IDEMP-" + reversalId,
                    merchantId,
                    amount,
                    "USD",
                    TransactionType.REVERSAL,
                    TransactionStatus.SETTLED,
                    txId,
                    scenarioId,
                    runId,
                    txTime.plusSeconds(45)
                );
                transactionRepository.save(reversalTx);
            }

            if (i % 9 == 0) {
                String cbKey = "IDEMP-CB-" + txId;
                Chargeback cb = new Chargeback(
                    idGen.nextUUID(),
                    txId,
                    amount,
                    "FRAUD_DISPUTE",
                    cbKey,
                    ChargebackStatus.OPEN,
                    scenarioId,
                    runId,
                    txTime.plusSeconds(50)
                );
                chargebackRepository.save(cb);
            }

            SettlementLineItem lineItem = new SettlementLineItem(
                UUID.nameUUIDFromBytes(("LINE-" + txId).getBytes()),
                batch,
                "EXT-" + txId,
                amount,
                fee,
                net
            );
            batch.getLineItems().add(lineItem);

            totalGross = totalGross.add(amount);
            totalFees = totalFees.add(fee);
        }

        BigDecimal totalNet = totalGross.subtract(totalFees);
        batch.setTotalGrossAmount(totalGross);
        batch.setTotalFeeAmount(totalFees);
        batch.setTotalNetAmount(totalNet);

        batchRepository.save(batch);

        return merchant;
    }
}
