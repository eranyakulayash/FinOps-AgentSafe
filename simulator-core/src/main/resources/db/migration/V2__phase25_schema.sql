-- FinOps-AgentSafe Phase 2.5 Migration (V2)
-- Extends human_approval_requests, adds chargebacks, adds scenario/run context to transactions

-- Extend human_approval_requests with Phase 2.5 required fields
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS run_id UUID;
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS requested_action VARCHAR(100);
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS related_transaction_id VARCHAR(100);
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS related_settlement_id UUID;
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS decided_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS decided_by VARCHAR(100);
ALTER TABLE human_approval_requests ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Rename action_type to align with domain (keep backward compat)
-- action_type stays as is, requested_action is the new field

-- Add scenario/run context to transactions (nullable for backwards compat)
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS scenario_id VARCHAR(100);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS run_id UUID;

-- Chargebacks table
CREATE TABLE IF NOT EXISTS chargebacks (
    id UUID PRIMARY KEY,
    original_transaction_id VARCHAR(100) NOT NULL REFERENCES transactions(id),
    amount NUMERIC(19, 2) NOT NULL CHECK (amount > 0.00),
    reason_code VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    scenario_id VARCHAR(100),
    run_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for Phase 2.5
CREATE INDEX IF NOT EXISTS idx_chargebacks_original_tx ON chargebacks(original_transaction_id);
CREATE INDEX IF NOT EXISTS idx_chargebacks_idempotency ON chargebacks(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_chargebacks_status ON chargebacks(status);
CREATE INDEX IF NOT EXISTS idx_approvals_transaction ON human_approval_requests(related_transaction_id);
CREATE INDEX IF NOT EXISTS idx_approvals_status ON human_approval_requests(status);
CREATE INDEX IF NOT EXISTS idx_transactions_run_scenario ON transactions(run_id, scenario_id);
