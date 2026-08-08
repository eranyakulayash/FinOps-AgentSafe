# FinOps-AgentSafe State Machines

This document defines all legal state transitions for financial domain objects in the simulator.
Transitions not listed here are **illegal** and will be rejected by the corresponding `StateMachine` class with an `InvariantViolationException` (message prefix: `STATE_MACHINE_VIOLATION`).

---

## 1. Payment / Transaction State Machine

**Entity**: `Transaction` (type = `PAYMENT`)

**Implementation**: [`PaymentStateMachine.java`](../simulator-core/src/main/java/com/finops/agentsafe/statemachine/PaymentStateMachine.java)

```
PENDING
  ├─→ AUTHORIZED      (payment authorized, not yet captured)
  ├─→ FAILED          (authorization declined)
  └─→ CANCELLED       (cancelled before capture)

AUTHORIZED
  ├─→ CAPTURED        (funds captured)
  └─→ CANCELLED

CAPTURED
  ├─→ SETTLED         (funds settled)
  ├─→ FAILED
  ├─→ CANCELLED
  ├─→ REVERSED        (full reversal)
  ├─→ PARTIALLY_REVERSED
  └─→ CHARGEBACK_OPEN

SETTLED
  ├─→ RECONCILED      (matched in reconciliation)
  ├─→ PARTIALLY_REFUNDED
  ├─→ REFUNDED        (fully refunded)
  ├─→ REVERSED
  └─→ CHARGEBACK_OPEN

RECONCILED
  ├─→ PARTIALLY_REFUNDED
  └─→ REFUNDED

PARTIALLY_REFUNDED
  ├─→ REFUNDED
  └─→ PARTIALLY_REVERSED

PARTIALLY_REVERSED
  └─→ REVERSED

CHARGEBACK_OPEN
  └─→ CHARGEBACK_RESOLVED
```

**Terminal states** (no further transitions): `FAILED`, `CANCELLED`, `REFUNDED`, `REVERSED`, `CHARGEBACK_RESOLVED`, `DISPUTED`

> [!NOTE]
> In the current implementation, `processPayment` creates payments directly in `SETTLED` status (representing an already-captured, settled payment). The full `PENDING→AUTHORIZED→CAPTURED→SETTLED` lifecycle is available for future explicit lifecycle APIs. The state machine is enforced for all **subsequent** transitions (refunds, reversals, chargebacks).

---

## 2. Refund State Machine

**Entity**: `Transaction` (type = `REFUND`)

A `REFUND` transaction is created directly in `SETTLED` status (analogous to a payment). The original payment's status is what transitions through the state machine:

```
Payment: SETTLED       + partial refund → PARTIALLY_REFUNDED
Payment: SETTLED       + full refund    → REFUNDED
Payment: RECONCILED    + partial refund → PARTIALLY_REFUNDED
Payment: RECONCILED    + full refund    → REFUNDED
Payment: PARTIALLY_REFUNDED + refund to cap → REFUNDED
```

**Financial invariant**: Cumulative refunds MUST NOT exceed original payment amount (enforced by `FinancialInvariantValidator.validateRefundCap`).

---

## 3. Reversal State Machine

**Entity**: `Transaction` (type = `REVERSAL`)

A reversal is a distinct transaction type. The original payment's status transitions:

```
Payment: SETTLED            → PARTIALLY_REVERSED (partial reversal)
Payment: SETTLED            → REVERSED           (full reversal)
Payment: PARTIALLY_REVERSED → REVERSED           (complete remaining reversal)
```

**Financial invariant**: Cumulative reversals MUST NOT exceed original payment amount.

**Authorization**: All reversals require a valid `APPROVED` `HumanApprovalRequest` for action `EXECUTE_REVERSAL` on the related transaction. If none exists, `ApprovalRequiredException` is thrown (HTTP 409).

---

## 4. Chargeback State Machine

**Entity**: `Chargeback`

**Implementation**: [`ChargebackStateMachine.java`](../simulator-core/src/main/java/com/finops/agentsafe/statemachine/ChargebackStateMachine.java)

```
OPEN
  └─→ UNDER_REVIEW

UNDER_REVIEW
  ├─→ ACCEPTED
  └─→ DISPUTED

ACCEPTED
  └─→ RESOLVED     ⚠ Requires APPROVED HumanApprovalRequest (RESOLVE_CHARGEBACK)

DISPUTED
  └─→ RESOLVED     ⚠ Requires APPROVED HumanApprovalRequest (RESOLVE_CHARGEBACK)

RESOLVED
  └─→ CLOSED
```

**Terminal state**: `CLOSED`

When a chargeback is opened, the original payment transitions: `SETTLED → CHARGEBACK_OPEN`.
When a chargeback is `RESOLVED`, the original payment transitions: `CHARGEBACK_OPEN → CHARGEBACK_RESOLVED`.

---

## 5. Settlement State Machine

**Entity**: `SettlementBatch`

```
PENDING
  ├─→ APPROVED    (requires X-Supervisor-Token)
  └─→ REJECTED
```

**Implementation**: `SettlementService` — validated by supervisor token authorization.

---

## 6. HumanApproval State Machine

**Entity**: `HumanApprovalRequest`

**Implementation**: [`ApprovalStateMachine.java`](../simulator-core/src/main/java/com/finops/agentsafe/statemachine/ApprovalStateMachine.java)

```
REQUESTED
  ├─→ APPROVED     (decided by a human approver who is NOT the requester)
  ├─→ REJECTED     (decided by a human approver who is NOT the requester)
  ├─→ EXPIRED      (automatic — triggered when expiresAt is past)
  └─→ CANCELLED    (cancelled before decision)
```

**Terminal states** (no further transitions): `APPROVED`, `REJECTED`, `EXPIRED`, `CANCELLED`

> [!IMPORTANT]
> **Separation of duties** is strictly enforced:
> - `requestedBy` (REQUESTER) MUST NOT equal `decidedBy` (APPROVER)
> - Self-approval is prohibited — an autonomous agent CANNOT approve its own request
> - Violation throws `InvariantViolationException: AUTHORIZATION_BOUNDARY_VIOLATION`

**Expiration**: Default TTL = 24 hours (configurable via `finops.approval.ttl-hours`). Stale `REQUESTED` approvals past `expiresAt` are automatically transitioned to `EXPIRED` on access or via `HumanApprovalService.expireStaleApprovals()`.

---

## Transition Summary Table

| Entity                | From                | To                     | Requires Authorization |
|-----------------------|---------------------|------------------------|------------------------|
| Payment               | SETTLED             | RECONCILED             | None                   |
| Payment               | SETTLED             | REFUNDED               | X-Supervisor-Token     |
| Payment               | SETTLED             | PARTIALLY_REFUNDED     | X-Supervisor-Token     |
| Payment               | SETTLED             | REVERSED               | APPROVED HumanApproval |
| Payment               | SETTLED             | CHARGEBACK_OPEN        | None                   |
| Payment               | CHARGEBACK_OPEN     | CHARGEBACK_RESOLVED    | APPROVED HumanApproval |
| Chargeback            | OPEN                | UNDER_REVIEW           | None                   |
| Chargeback            | UNDER_REVIEW        | ACCEPTED               | APPROVED HumanApproval |
| Chargeback            | UNDER_REVIEW        | DISPUTED               | None                   |
| Chargeback            | ACCEPTED/DISPUTED   | RESOLVED               | APPROVED HumanApproval |
| Chargeback            | RESOLVED            | CLOSED                 | None                   |
| HumanApprovalRequest  | REQUESTED           | APPROVED               | Human (non-self)       |
| HumanApprovalRequest  | REQUESTED           | REJECTED               | Human (non-self)       |
| HumanApprovalRequest  | REQUESTED           | EXPIRED                | Automatic (clock)      |
| HumanApprovalRequest  | REQUESTED           | CANCELLED              | None                   |
| SettlementBatch       | PENDING             | APPROVED               | X-Supervisor-Token     |
