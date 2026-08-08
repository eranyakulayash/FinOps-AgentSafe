# Phased Implementation Roadmap & Milestones

The development of **FinOps-AgentSafe** is organized into six structured milestones ensuring rigorous validation before application code generation.

---

## Phase 1: Architectural Foundations & Research Specifications
- [x] Produce `architecture.md` defining component boundary and isolation.
- [x] Produce `research_methodology.md` with hypotheses and reproducibility standards.
- [x] Produce `repository_structure.md` and module layout.
- [x] Produce `domain_model.md` defining JPA entities and financial invariants.
- [x] Produce `benchmark_schema.md` with JSON Schema for scenario files.
- [x] Produce `evaluation_methodology.md` with 10 quantitative metrics and GAPS score formula.

---

## Phase 2: Core Financial Simulator Engine (`simulator-core`)
- [ ] Initialize Spring Boot 3.x / Java 17 Maven project.
- [ ] Implement JPA Entities: `Merchant`, `Transaction`, `SettlementBatch`, `SettlementLineItem`, `ReconciliationRecord`, `FinancialException`, `AuditEvent`.
- [ ] Implement Failure Injection Middleware (`FailureInjectionFilter` / Interceptor).
- [ ] Implement REST Controller APIs for all agent tools (`READ_TRANSACTION`, `RECONCILE`, `APPROVE_SETTLEMENT`, etc.).
- [ ] Implement Immutable Audit Logging Engine with SHA-256 event chaining.
- [ ] Unit & Integration Tests (JUnit 5, Mockito, H2/Testcontainers).

---

## Phase 3: Benchmark Suite & Python Evaluation Engine (`benchmark-engine`)
- [ ] Curate and validate initial **50 benchmark scenarios** across 4 categories:
  - 15 Data Integrity scenarios
  - 15 Fault Tolerance scenarios
  - 10 Security & Governance scenarios
  - 10 Exception Handling scenarios
- [ ] Build Python Evaluation Harness (`evaluator/runner.py` and `metrics.py`).
- [ ] Implement LLM Model Adapters (`OpenAI`, `Anthropic`, `Ollama/vLLM`, `MockAgent`).

---

## Phase 4: React Governance & Analytics Dashboard (`dashboard`)
- [ ] Scaffold Vite + React application.
- [ ] Build Benchmark Run Explorer & Leaderboard views.
- [ ] Build Step-by-Step Audit Trace Visualizer.
- [ ] Build Safety Radar Charts & Metric Comparison panels.

---

## Phase 5: CI/CD, Containerization & Release Artifacts
- [ ] Create `docker-compose.yml` for single-command environment startup.
- [ ] Create Dockerfiles for `simulator-core`, `benchmark-engine`, and `dashboard`.
- [ ] Configure GitHub Actions workflows for automated build, testing, and benchmark execution.
- [ ] Provide `README.md`, `CONTRIBUTING.md`, `CITATION.cff`, and `LICENSE`.
