package com.finops.agentsafe.service;

import com.finops.agentsafe.domain.Merchant;
import com.finops.agentsafe.domain.SettlementBatch;
import com.finops.agentsafe.domain.SettlementLineItem;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.enums.SettlementStatus;
import com.finops.agentsafe.enums.TransactionStatus;
import com.finops.agentsafe.enums.TransactionType;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.SettlementBatchRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.UUID;

@Service
public class SyntheticDataService {

    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;
    private final SettlementBatchRepository batchRepository;

    public SyntheticDataService(MerchantRepository merchantRepository,
                                TransactionRepository transactionRepository,
                                SettlementBatchRepository batchRepository) {
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public Merchant seedSyntheticScenario(long seed, String merchantName, int transactionCount) {
        Random rng = new Random(seed);

        UUID merchantId = UUID.nameUUIDFromBytes(("MCH-" + seed).getBytes());
        BigDecimal feeRate = BigDecimal.valueOf(2.50).setScale(2, RoundingMode.HALF_UP);
        Merchant merchant = new Merchant(merchantId, merchantName, feeRate, "ACTIVE");
        merchantRepository.save(merchant);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;

        UUID batchId = UUID.nameUUIDFromBytes(("STL-" + seed).getBytes());
        SettlementBatch batch = new SettlementBatch(
            batchId,
            merchantId,
            "settlement_batch_" + seed + ".csv",
            "Deterministic synthetic settlement batch for scenario " + seed,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            SettlementStatus.UNPROCESSED
        );

        for (int i = 1; i <= transactionCount; i++) {
            String txId = String.format("TX-%d-%04d", seed, i);
            String idempotencyKey = "IDEMP-" + txId;
            BigDecimal amount = BigDecimal.valueOf(50 + rng.nextInt(450)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = amount.multiply(feeRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal net = amount.subtract(fee);

            Transaction tx = new Transaction(
                txId,
                idempotencyKey,
                merchantId,
                amount,
                "USD",
                TransactionType.PAYMENT,
                TransactionStatus.SETTLED,
                null
            );
            transactionRepository.save(tx);

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
