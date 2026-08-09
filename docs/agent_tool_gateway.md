# Agent Tool Gateway Specification

## Architecture & Provider Neutrality
The `AgentToolGateway` provides a provider-neutral tool execution layer between AI agents and financial simulator services.
It exposes a constrained set of 12 financial operations tools.

```
Agent / Model Framework (RuleBasedAgent, Future LLM)
       │
       ▼
AgentToolGateway (AgentToolExecutor)
       │
       ▼
AgentToolPolicyEngine (Validates scenario bounds, step limit, HITL requirements)
       │
       ▼
Financial Simulator Services (PaymentService, ChargebackService, HumanApprovalService)
```

## Available Tools & Risk Levels

| Tool Name | Risk Level | Idempotent | Description |
|-----------|------------|------------|-------------|
| `READ_TRANSACTION` | READ_ONLY | Yes | Retrieve transaction details by ID |
| `SEARCH_TRANSACTIONS` | READ_ONLY | Yes | Search transactions by merchant ID |
| `READ_SETTLEMENT` | READ_ONLY | Yes | Retrieve settlement batch details |
| `READ_RECONCILIATION` | READ_ONLY | Yes | Retrieve transaction reconciliation status |
| `RECONCILE_TRANSACTION` | LOW_RISK_WRITE | Yes | Reconcile internal transaction with settlement line item |
| `CREATE_EXCEPTION` | LOW_RISK_WRITE | Yes | Log a financial exception for discrepancy |
| `REQUEST_HUMAN_APPROVAL` | HIGH_RISK_WRITE | Yes | Request supervisor approval for high-risk action |
| `CHECK_APPROVAL_STATUS` | READ_ONLY | Yes | Check status of pending HumanApprovalRequest |
| `PROPOSE_SETTLEMENT_ACTION` | LOW_RISK_WRITE | Yes | Propose settlement action for review |
| `RETRY_OPERATION` | LOW_RISK_WRITE | Yes | Request retry of failed operation within bounds |
| `ESCALATE_TO_HUMAN` | HIGH_RISK_WRITE | Yes | Escalate unresolvable scenario to human operator |
| `READ_AUDIT_SUMMARY` | READ_ONLY | Yes | Retrieve audit logs for scenario or run ID |

## Security Restrictions
- No direct SQL execution tools are exposed.
- No direct database repository access is provided.
- **SELF-APPROVAL BARRIER**: The gateway explicitly omits any `APPROVE_HUMAN_REQUEST` tool. Autonomous agents CANNOT approve human approval requests.
