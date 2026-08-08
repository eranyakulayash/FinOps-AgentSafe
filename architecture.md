# FinOps-AgentSafe System Architecture Specification

## Executive Summary

**FinOps-AgentSafe** is a research-grade, reproducible benchmark platform designed to evaluate the safety, fault tolerance, data integrity, and auditability of Large Language Model (LLM) autonomous agents operating in simulated financial payment reconciliation and settlement workflows. 

The architecture enforces strict isolation between agent reasoning, financial execution, failure injection, and audit verification. It guarantees **zero real-world financial risk** by utilizing deterministic synthetic simulators.

---

## High-Level Architecture Diagram

```mermaid
flowchart TB
    subgraph Agent Framework Under Test (AUT)
        Agent[Autonomous LLM Agent / Adapter]
        LLM[LLM Provider API\nOpenAI / Anthropic / Ollama]
        Agent <--> LLM
    end

    subgraph Benchmark Harness (Python Evaluation Engine)
        Runner[Benchmark Runner]
        ScenarioLoader[JSONL Scenario Engine]
        MetricsEngine[Evaluation & Metrics Engine]
        Runner --> ScenarioLoader
        Runner --> Agent
        Runner --> MetricsEngine
    end

    subgraph Simulator Gateway & Fault Injection Middleware (Spring Boot)
        API[REST Tool APIs]
        Interceptor[Failure Injection Interceptor]
        Authz[Authorization & Boundary Guardrails]
        Validator[FinancialInvariantValidator]
        
        API --> Interceptor
        Interceptor --> Authz
        Authz --> Validator
    end

    subgraph Simulated Core Engine (Java 17 / Spring Data JPA)
        PaymentSvc[Payment & Refund Service]
        ReconcileSvc[Reconciliation Service]
        SettlementSvc[Settlement Calculation Service]
        AuditSvc[Immutable Audit Logging Service]
        
        Validator --> PaymentSvc
        Validator --> ReconcileSvc
        Validator --> SettlementSvc
        PaymentSvc --> AuditSvc
        ReconcileSvc --> AuditSvc
        SettlementSvc --> AuditSvc
    end

    subgraph Storage Layer
        PostgreSQL[(PostgreSQL Sandbox Database)]
        AuditDB[(Immutable Audit Event Store)]
        PaymentSvc --> PostgreSQL
        ReconcileSvc --> PostgreSQL
        SettlementSvc --> PostgreSQL
        AuditSvc --> AuditDB
    end
```

---

## Centralized Financial Invariants & Validation

All application write operations pass through `FinancialInvariantValidator` before JPA persistence. Controllers and services delegate validation to this single core engine:

- **Monetary Types**: Strictly `BigDecimal` (Scale 2, `ROUND_HALF_UP`). Zero float/double types allowed.
- **Double-Entry Invariant**: $\text{Gross Amount} - \text{Fee Amount} = \text{Net Settlement Amount}$.
- **Refund Cap Invariant**: Cumulative refunds for payment $P \le P.\text{amount}$.
- **Idempotency Guarantee**: All write tools require an `idempotency_key` header/parameter.
- **Optimistic Locking**: Enforced via `@Version` attributes on core financial entities.

---

## Tool Classification & Action Boundaries

Agent operations are restricted to REST API endpoints categorized by action risk level:

| Action Risk Level | Tools Included | Authorization Required |
| :--- | :--- | :--- |
| `READ_ONLY` | `READ_TRANSACTION`, `SEARCH_TRANSACTION`, `GET_SETTLEMENT_FILE` | None |
| `LOW_RISK_WRITE` | `RECONCILE`, `CREATE_EXCEPTION`, `RETRY_TOOL` | Standard Agent Session Token |
| `HIGH_RISK_WRITE` | `EXECUTE_REFUND`, `APPROVE_SETTLEMENT`, `REJECT_SETTLEMENT` | Mandatory Supervisory Authorization Token |
| `HUMAN_APPROVAL_REQUIRED` | `REQUEST_APPROVAL`, `ESCALATE_TO_HUMAN` | Delegates execution to human operator |

---

## Failure Injection Isolation

System, data, and safety failures are managed by `FailureInjectionInterceptor` isolated from domain services:

1. **Data Failures**: Amount mismatch, duplicate transactions, missing transactions, invalid states, conflicting settlements.
2. **System Failures**: API timeouts (504), rate limits (429), HTTP 5xx errors, malformed responses, temporary dependency outages.
3. **Agent Safety Failures**: Unauthorized action requests, contradictory instructions, indirect prompt injections, missing evidence payloads.

---

## Auditability & Event Structure

`AuditEvent` entities capture complete execution traces without requiring private LLM chain-of-thought:
- `run_id`, `scenario_id`, `timestamp`
- `actor_agent_id`, `requested_action`, `tool_used`
- `input_parameters_hash`, `authz_decision`, `execution_result`
- `before_state_ref`, `after_state_ref`
- `injected_failure_type` (if active)
- `human_approval_info`, `agent_reasoning_summary`
- Cryptographic SHA-256 hash chaining (`prev_hash` -> `current_hash`)

---

## Research Release & Artifact Layout

The repository is structured to support reproducible research publishing:
- `scenarios/`: Public benchmark JSONL dataset (100% synthetic).
- `leaderboard/`: Versioned benchmark results and evaluation scripts.
- `hf_dataset/`: Pipelines for Hugging Face Dataset export.
- `paper/`: Latex/BibTeX research paper artifacts.
- `CITATION.cff`: Academic attribution specification.
