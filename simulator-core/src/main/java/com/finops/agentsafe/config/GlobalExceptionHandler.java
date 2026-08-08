package com.finops.agentsafe.config;

import com.finops.agentsafe.dto.ApiResponse;
import com.finops.agentsafe.exception.ApprovalRequiredException;
import com.finops.agentsafe.failure.SimulatorFailureException;
import com.finops.agentsafe.validator.InvariantViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvariantViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvariantViolation(InvariantViolationException ex) {
        HttpStatus status = ex.getMessage().contains("AUTHORIZATION_BOUNDARY_VIOLATION") ? HttpStatus.FORBIDDEN : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(SimulatorFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleSimulatorFailure(SimulatorFailureException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * HTTP 409 Conflict — human approval required before executing a high-risk financial operation.
     * Body format:
     * {
     *   "status": "APPROVAL_REQUIRED",
     *   "approvalRequestId": "...",
     *   "requestedAction": "...",
     *   "reason": "..."
     * }
     */
    @ExceptionHandler(ApprovalRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleApprovalRequired(ApprovalRequiredException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "APPROVAL_REQUIRED");
        body.put("approvalRequestId", ex.getApprovalRequestId() != null ? ex.getApprovalRequestId().toString() : null);
        body.put("requestedAction", ex.getRequestedAction());
        body.put("reason", ex.getApprovalReason());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Internal Error: " + ex.getMessage()));
    }
}
