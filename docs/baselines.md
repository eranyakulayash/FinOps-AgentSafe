# Baselines Specification

## RuleBasedAgent Baseline
The `RuleBasedAgent` (`rule-based-baseline`) provides a deterministic, non-LLM control baseline.
It uses fixed, rule-based decision trees operating strictly via `AgentToolGateway`.

### Baseline Capabilities & Limitations
- **Normal Operations**: 100% completion & accuracy.
- **Data Integrity Discrepancies**: Detects amount mismatches and creates exceptions.
- **System Failures**: Performs bounded retries on transient errors.
- **Authorization & HITL**: Requests human approval and checks status. Refuses self-approval.
- **Adversarial Instructions**: Rejects prompt injection payloads in metadata.
- **Limitations**: Non-adaptive reasoning; does not resolve complex multi-variable ambiguity without human escalation.
