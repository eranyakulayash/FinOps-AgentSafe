package com.finops.agentsafe.controller;

import com.finops.agentsafe.domain.Chargeback;
import com.finops.agentsafe.domain.Transaction;
import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.dto.ChargebackRequest;
import com.finops.agentsafe.dto.PaymentRequest;
import com.finops.agentsafe.dto.RefundRequest;
import com.finops.agentsafe.dto.ReversalRequest;
import com.finops.agentsafe.service.ChargebackService;
import com.finops.agentsafe.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction API", description = "Endpoints for payments, refunds, reversals, chargebacks, and transaction lookups")
public class TransactionController {

    private final PaymentService paymentService;
    private final ChargebackService chargebackService;

    public TransactionController(PaymentService paymentService, ChargebackService chargebackService) {
        this.paymentService = paymentService;
        this.chargebackService = chargebackService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "READ_TRANSACTION (READ_ONLY): Query transaction details by ID")
    public ResponseEntity<ApiResponse<Transaction>> getTransaction(@PathVariable String id) {
        return paymentService.getTransaction(id)
            .map(tx -> ResponseEntity.ok(ApiResponse.success("Transaction retrieved", tx)))
            .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("Transaction not found: " + id)));
    }

    @GetMapping("/merchant/{merchantId}")
    @Operation(summary = "SEARCH_TRANSACTION (READ_ONLY): Query transactions by merchant ID")
    public ResponseEntity<ApiResponse<List<Transaction>>> getTransactionsByMerchant(@PathVariable UUID merchantId) {
        List<Transaction> list = paymentService.getTransactionsByMerchant(merchantId);
        return ResponseEntity.ok(ApiResponse.success("Merchant transactions retrieved", list));
    }

    @PostMapping("/payment")
    @Operation(summary = "EXECUTE_PAYMENT (LOW_RISK_WRITE): Process a payment transaction")
    public ResponseEntity<ApiResponse<Transaction>> processPayment(@RequestBody PaymentRequest req) {
        Transaction tx = paymentService.processPayment(
            req.getTransactionId(),
            req.getIdempotencyKey(),
            req.getMerchantId(),
            req.getAmount(),
            req.getCurrency()
        );
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", tx));
    }

    @PostMapping("/refund")
    @Operation(summary = "EXECUTE_REFUND (HIGH_RISK_WRITE): Issue a refund against an original payment. Requires X-Supervisor-Token")
    public ResponseEntity<ApiResponse<Transaction>> processRefund(
            @RequestBody RefundRequest req,
            @RequestHeader(value = "X-Supervisor-Token", required = false) String token) {
        Transaction refund = paymentService.processRefund(
            req.getRefundTxId(),
            req.getIdempotencyKey(),
            req.getOriginalPaymentId(),
            req.getRefundAmount(),
            token
        );
        return ResponseEntity.ok(ApiResponse.success("Refund processed successfully", refund));
    }

    @PostMapping("/reversal")
    @Operation(summary = "EXECUTE_REVERSAL (HIGH_RISK_WRITE): Issue a reversal. Requires prior APPROVED human approval request.")
    public ResponseEntity<ApiResponse<Transaction>> processReversal(@RequestBody ReversalRequest req) {
        Transaction reversal = paymentService.processReversal(
            req.getReversalTxId(),
            req.getIdempotencyKey(),
            req.getOriginalPaymentId(),
            req.getReversalAmount(),
            req.getRequestedBy()
        );
        return ResponseEntity.ok(ApiResponse.success("Reversal processed successfully", reversal));
    }

    @PostMapping("/chargeback")
    @Operation(summary = "OPEN_CHARGEBACK (HIGH_RISK_WRITE): Open a chargeback dispute against an eligible payment")
    public ResponseEntity<ApiResponse<Chargeback>> openChargeback(@RequestBody ChargebackRequest req) {
        Chargeback chargeback = chargebackService.openChargeback(
            req.getOriginalTransactionId(),
            req.getAmount(),
            req.getReasonCode(),
            req.getIdempotencyKey(),
            req.getScenarioId(),
            req.getRunId()
        );
        return ResponseEntity.ok(ApiResponse.success("Chargeback opened successfully", chargeback));
    }
}
