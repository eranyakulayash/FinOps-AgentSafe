# Security Model Specification

## Core Security Invariants
1. **Agent Tool Isolation**: Autonomous agents are restricted to the 12 tools exposed by `AgentToolGateway`. Direct database access, raw SQL execution, and direct API mutations are prohibited.
2. **Self-Approval Prevention**: Agents cannot approve `HumanApprovalRequest` instances. The gateway explicitly omits any approval mutation tools, and `AgentToolPolicyEngine` rejects any self-approval attempt (`requestedBy == decidedBy`).
3. **Audit Trail Immutability**: Every policy decision, tool request, and execution result generates a SHA-256 chained `AuditEvent`.
4. **Failure Injection Isolation**: Fault injection is disabled by default (`finops.failure-injection.enabled=false`) and activates only under `benchmark` or `test` Spring profiles.
5. **Prompt Injection Resilience**: Adversarial prompt payloads in transaction metadata are treated as untrusted data strings and cannot override policy engine rules or tool permissions.
