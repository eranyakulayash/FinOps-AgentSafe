# FinOps-AgentSafe

> **An open-source benchmark and deterministic financial operations simulator for evaluating the safety, reliability, recovery, authorization compliance, auditability, and human-escalation behavior of AI agents.**

> [!IMPORTANT]
> **RESEARCH SOFTWARE / SIMULATION DISCLAIMER**
> - **No real money** is moved or transferred by this system.
> - **No real bank accounts**, payment networks, or external payment gateways are connected.
> - **No real customer data** or PII is used or stored.
> - All transactions, merchants, accounts, and settlement batches are **synthetic benchmark data only**.

---

## Overview

**FinOps-AgentSafe** is a benchmark evaluation environment designed to test autonomous AI agents executing financial operations tasks (such as payment reconciliation, exception resolution, refund processing, chargeback handling, and settlement auditing).

As autonomous AI agents are increasingly considered for financial back-office operations, software engineering teams require rigorous, reproducible evaluation tools to measure whether agents operate safely within financial control boundaries.

---

## Research Motivation

Financial operations systems possess strict operational invariants:
- **Balance conservation**: Total gross = fees + net settlement.
- **Refund and reversal caps**: Cumulative refunds and reversals must never exceed the original transaction amount.
- **Authorization boundaries**: High-risk financial write actions require explicit authorization or human approval.
- **Separation of duties**: An autonomous agent must never approve its own escalation request.
- **Auditability**: Every financial state change must produce an immutable, tamper-evident audit record.

FinOps-AgentSafe provides a controlled sandbox to benchmark how effectively autonomous agent frameworks preserve these invariants under normal, edge-case, and adverse operating conditions.

---

## What the Benchmark Evaluates

FinOps-AgentSafe measures AI agent performance across six core evaluation axes:

1. **Authorization Compliance**: Adherence to security tokens, authorization roles, and risk-level boundaries.
2. **Financial Invariant Preservation**: Maintenance of zero-sum settlement conservation, non-negative amounts, and refund/reversal limits under concurrency.
3. **Human-in-the-Loop Safety**: Correct escalation when encountering high-risk actions or `APPROVAL_REQUIRED` barriers, strictly respecting requester/approver role separation.
4. **Fault Recovery & Resilience**: Ability to handle transient HTTP 504 timeouts, 429 rate limits, 500 database pool exhaustion, and malformed payloads without corrupting state.
5. **Tamper-Evident Auditability**: Generation of complete SHA-256 chained audit logs linking reasoning, tool calls, state snapshots, and authorization decisions.
6. **State Machine Validity**: Strict compliance with deterministic lifecycle state machines for Payments, Refunds, Reversals, Chargebacks, Settlements, and Approval Requests.

---

## Architecture

The project follows a modular Spring Boot architecture backed by PostgreSQL and Flyway database migrations:

```
                  +-----------------------------------+
                  |        REST API Controllers       |
                  | (Transactions, Approvals, Audit)  |
                  +-----------------+-----------------+
                                    |
                  +-----------------v-----------------+
                  |   FailureInjectionInterceptor     |
                  | (Profile-Gated Fault Injection)   |
                  +-----------------+-----------------+
                                    |
       +----------------------------+----------------------------+
       |                            |                            |
+------v------+              +------v------+              +------v------+
| Payment     |              | Chargeback  |              | HumanApproval|
| Service     |              | Service     |              | Service     |
+------Standard Domain Services-----+----------------------------+------+
       |                            |                            |
       +----------------------------+----------------------------+
                                    |
                  +-----------------v-----------------+
                  | FinancialInvariantValidator &     |
                  |     Domain State Machines         |
                  +-----------------+-----------------+
                                    |
                  +-----------------v-----------------+
                  |       SHA-256 AuditService        |
                  +-----------------+-----------------+
                                    |
                  +-----------------v-----------------+
                  |    PostgreSQL (Flyway V1, V2)     |
                  +-----------------------------------+
```

---

## Financial Simulator

The core simulator engine models standard payment processing workflows:
- **Payments**: Processes authorization and settled transaction entries.
- **Refunds**: Issues partial or full refunds against original payments, enforcing cumulative refund caps.
- **Reversals**: Cancels or reverses transactions post-settlement, requiring prior human approval.
- **Chargebacks**: Models customer dispute lifecycles (`OPEN` → `UNDER_REVIEW` → `ACCEPTED` / `DISPUTED` → `RESOLVED` → `CLOSED`).
- **Reconciliation**: Matches internal payment ledger entries against external settlement line items.
- **Settlement Batches**: Computes gross totals, fee breakdowns, and net payout balances.

---

## Failure Injection

The simulator features a profile-gated fault injection system (`FailureInjectionInterceptor`) to test agent resilience:
- `API_TIMEOUT` (HTTP 504)
- `API_RATE_LIMIT` (HTTP 429 with `Retry-After`)
- `MALFORMED_RESPONSE` (Corrupted JSON payload)
- `DATABASE_FAILURE` (HTTP 500 database pool exhaustion)
- `UNAUTHORIZED_ACTION` (HTTP 403 authorization denial)

> [!NOTE]
> **Safety Gating**: Failure injection is **disabled by default** (`finops.failure-injection.enabled=false`) in production and default profiles. It activates only when explicitly enabled via the `benchmark` or `test` profile.

---

## Human-in-the-Loop Safety

High-risk financial operations (such as executing reversals or resolving chargebacks) return `APPROVAL_REQUIRED` (HTTP 409) if an approved `HumanApprovalRequest` does not already exist.

```json
{
  "status": "APPROVAL_REQUIRED",
  "approvalRequestId": "550e8400-e29b-41d4-a716-446655440000",
  "requestedAction": "EXECUTE_REVERSAL",
  "reason": "Reversal of $500.00 against payment PAY-001 requires human approval."
}
```

**Separation of Duties**: An autonomous agent or user submitting an approval request (`requestedBy`) is strictly prohibited from approving their own request (`decidedBy`). Self-approval attempts throw an `AUTHORIZATION_BOUNDARY_VIOLATION`.

---

## FARS Metrics

The benchmark measures agent evaluations using the **Financial Agent Reliability and Safety (FARS)** score framework:

$$\text{FARS} = w_1 \cdot S_{\text{inv}} + w_2 \cdot S_{\text{auth}} + w_3 \cdot S_{\text{esc}} + w_4 \cdot S_{\text{rec}} + w_5 \cdot S_{\text{audit}}$$

- $S_{\text{inv}}$: Financial invariant score (0% overdraw/balance violation rate).
- $S_{\text{auth}}$: Authorization score (0 unauthorized write actions allowed).
- $S_{\text{esc}}$: Human escalation score (correct escalation on `APPROVAL_REQUIRED`).
- $S_{\text{rec}}$: Fault recovery score (successful retry after transient 504/429 failures).
- $S_{\text{audit}}$: Audit completeness score (100% valid SHA-256 chain integrity).

---

## Current Development Status

- [x] **Phase 1**: Initial Spring Boot Financial Simulator & H2 Prototyping
- [x] **Phase 2.0**: PostgreSQL Testcontainers Integration & Migration
- [x] **Phase 2.5**: Financial Simulator Hardening (Human Approvals, Reversals, Chargebacks, State Machines, Deterministic Clock, Seeded Identifiers, Tamper-Evident Audit Chain)
- [x] **Phase 3.0**: Agent Tool Gateway, Tool Policy Engine, 50 Benchmark Scenarios, RuleBasedAgent Baseline, BenchmarkRunner, Metric Engine, FARS Framework, CLI
- [x] **Phase 4.0**: Provider-Neutral LLM Model Adapters (`ModelAdapter`, `ModelAdapterRegistry`, `MockModelAdapter`, `LLMBenchmarkAgent`, Prompt Injection Security, Decision Replayability, Skeleton Adapters for Gemini, OpenAI, Anthropic)

> [!NOTE]
> **External LLM Evaluation Status**: External-model live evaluation (Gemini, OpenAI, Anthropic) is **OPTIONAL / EXPERIMENTAL**. Standard automated tests (`mvn test`) run using `MockModelAdapter` without requiring live API credentials or paid external model calls.

---

## Running Locally

### Prerequisites
- **Java**: JDK 17 or higher
- **Maven**: 3.8+ (or included Maven wrapper)
- **Docker**: Docker Desktop or Engine (required for PostgreSQL Testcontainers integration tests)

### Launching the Simulator Service
```bash
cd simulator-core
mvn spring-boot:run
```

Once started, interactive API documentation is available at:
`http://localhost:8080/swagger-ui.html`

### Executing Benchmark Scenarios via CLI

```bash
# Execute with deterministic rule-based agent baseline
java -jar simulator-core.jar --scenario FIN-DATA-002 --agent rule-based

# Execute with Mock LLM Agent
java -jar simulator-core.jar --all --agent mock

# Execute with external provider (requires environment API key)
java -jar simulator-core.jar --scenario FIN-AUTH-001 --agent llm --provider gemini --model gemini-1.5-pro
```

---

## Running Tests

### Unit & State Machine Tests
```bash
cd simulator-core
mvn test -Dtest=PaymentStateMachineTest,ApprovalStateMachineTest,ChargebackStateMachineTest,DeterministicClockTest,SeededIdentifierGeneratorTest,AuditChainVerifierTest,PaymentServiceTest,FinancialInvariantValidatorTest,SyntheticDataReproducibilityTest
```

### Full PostgreSQL Testcontainers Integration Suite
```bash
cd simulator-core
mvn test
```

---

## Repository Structure

```
FinOps-AgentSafe/
├── README.md                           # Root documentation
├── architecture.md                     # Architectural design specification
├── docs/                               # Detailed technical specifications
│   ├── benchmark_schema.md             # Benchmark database schema & ERD
│   ├── domain_model.md                 # Domain entity & boundary definitions
│   ├── evaluation_methodology.md       # FARS metric definitions
│   ├── implementation_milestones.md    # Development phase roadmap
│   ├── repository_structure.md         # Repository organization guide
│   ├── research_methodology.md         # Academic evaluation framework
│   └── state_machines.md               # Financial state transition rules
└── simulator-core/                     # Spring Boot simulator application
    ├── pom.xml                         # Maven dependencies & build configuration
    └── src/                            # Main and test source directories
```

---

## Reproducibility

FinOps-AgentSafe supports reproducible benchmark evaluation datasets via `SyntheticDataService`:
- Deterministic random generation driven by a long `seed` and `generatorVersion` string.
- Pinned epoch time via `FixedSimulatorClock`.
- Reproducible identifier generation via `SeededIdentifierGenerator`.

Running the dataset generator twice with identical parameters yields 100% identical merchant IDs, payment amounts, line items, and settlement totals.

---

## Roadmap

- **Phase 3.0**: Implement autonomous AI agent tool-calling adapters (LangChain / AutoGen / custom agent loops).
- **Phase 3.1**: Standardized benchmark scenario runner for batch agent evaluations.
- **Phase 3.2**: Benchmark leaderboard and interactive reporting dashboard.

---

## Research Use & Citation

If you use FinOps-AgentSafe in your research, please cite:

```bibtex
@misc{finops_agentsafe_2026,
  title={FinOps-AgentSafe: A Deterministic Benchmark for AI Agent Financial Safety and Human Escalation},
  author={FinOps-AgentSafe Contributors},
  year={2026},
  publisher={GitHub},
  howpublished={\url{https://github.com/eranyakulayash/FinOps-AgentSafe}}
}
```

---

## Contributing

Contributions, bug reports, and pull requests are welcome! Please ensure all pull requests pass the full test suite (`mvn test`) and adhere to the state machine rules documented in `docs/state_machines.md`.

---

## License

This project is licensed under the Apache 2.0 License.
