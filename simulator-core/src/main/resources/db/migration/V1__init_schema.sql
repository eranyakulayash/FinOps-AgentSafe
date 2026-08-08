-- FinOps-AgentSafe Initial Database Migration (V1)
-- Enforces PostgreSQL DDL with strict check constraints and numeric scale

CREATE TABLE merchants (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    fee_rate_percentage NUMERIC(5, 2) NOT NULL CHECK (fee_rate_percentage >= 0.00 AND fee_rate_percentage <= 100.00),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transactions (
    id VARCHAR(100) PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0.00),
    currency VARCHAR(10) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    original_payment_id VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES merchants(id),
    file_reference VARCHAR(255) NOT NULL,
    notes TEXT,
    total_gross_amount NUMERIC(19, 2) NOT NULL CHECK (total_gross_amount >= 0.00),
    total_fee_amount NUMERIC(19, 2) NOT NULL CHECK (total_fee_amount >= 0.00),
    total_net_amount NUMERIC(19, 2) NOT NULL CHECK (total_net_amount >= 0.00),
    status VARCHAR(50) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_balance_conservation CHECK (total_gross_amount - total_fee_amount = total_net_amount)
);

CREATE TABLE settlement_line_items (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES settlement_batches(id) ON DELETE CASCADE,
    external_tx_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0.00),
    fee NUMERIC(19, 2) NOT NULL CHECK (fee >= 0.00),
    net_amount NUMERIC(19, 2) NOT NULL CHECK (net_amount >= 0.00),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_line_balance CHECK (amount - fee = net_amount)
);

CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL REFERENCES transactions(id),
    settlement_line_item_id UUID REFERENCES settlement_line_items(id),
    discrepancy_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    match_status VARCHAR(50) NOT NULL,
    reconciled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transaction_reconciled UNIQUE (transaction_id)
);

CREATE TABLE financial_exceptions (
    id UUID PRIMARY KEY,
    transaction_id VARCHAR(100),
    batch_id UUID,
    exception_type VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    scenario_id VARCHAR(100) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    requested_action VARCHAR(100) NOT NULL,
    tool_used VARCHAR(100) NOT NULL,
    risk_level VARCHAR(50) NOT NULL,
    input_payload_hash VARCHAR(255),
    authz_decision VARCHAR(50) NOT NULL,
    execution_result VARCHAR(50) NOT NULL,
    before_state_ref VARCHAR(255),
    after_state_ref VARCHAR(255),
    injected_failure_type VARCHAR(100),
    human_approval_info TEXT,
    reasoning_summary TEXT,
    prev_hash VARCHAR(255) NOT NULL,
    current_hash VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE human_approval_requests (
    id UUID PRIMARY KEY,
    scenario_id VARCHAR(100) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    requested_by VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    reason TEXT,
    approver_token VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_transactions_merchant ON transactions(merchant_id);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);
CREATE INDEX idx_reconciliation_tx ON reconciliation_records(transaction_id);
CREATE INDEX idx_audit_run_scenario ON audit_events(run_id, scenario_id);
