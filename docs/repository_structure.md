# Repository Structure Specification

The `FinOps-AgentSafe` project uses a multi-module repository layout dividing backend simulation, evaluation harness, benchmark scenario datasets, frontend dashboard, infrastructure, and research documentation.

```
FinOps-AgentSafe/
├── .github/                           # CI/CD Workflows
│   └── workflows/
│       ├── build-and-test.yml         # Java & Python build & unit testing workflow
│       └── benchmark-ci.yml           # Automated benchmark evaluation workflow
├── docs/                              # Research & Architectural Documentation
│   ├── architecture.md                # System Architecture & Component Interaction
│   ├── research_methodology.md        # Research Hypothesis & Evaluation Framework
│   ├── repository_structure.md        # File Layout & Module Breakdown
│   ├── domain_model.md                # Data Models & Financial Invariants
│   ├── benchmark_schema.md            # JSONL Benchmark Scenario Specification
│   ├── evaluation_methodology.md      # Quantitative Metrics & Scoring Formulae
│   └── implementation_milestones.md   # Phased Delivery Roadmap
├── simulator-core/                    # Spring Boot Simulating Engine (Java 17)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/finops/agentsafe/
│   │   │   │   ├── config/            # Security, REST, JPA configurations
│   │   │   │   ├── controller/        # REST Endpoints (Payment, Reconciliation, Settlement)
│   │   │   │   ├── domain/            # Entities (Transaction, Settlement, Exception, Audit)
│   │   │   │   ├── failure/           # Failure Injection Framework & Middleware
│   │   │   │   ├── repository/        # Spring Data JPA Repositories
│   │   │   │   ├── service/           # Financial Business Logic & Invariant Enforcement
│   │   │   │   └── audit/             # Immutable Audit Event Logging
│   │   │   └── resources/
│   │   │       ├── application.yml    # Application Config
│   │   │       └── schema.sql         # PostgreSQL DDL
│   │   └── test/                      # JUnit 5 & Integration Tests
│   ├── pom.xml                        # Maven Build Script
│   └── Dockerfile                     # Spring Boot Docker Image
├── benchmark-engine/                  # Python Evaluation Engine & Benchmark Harness
│   ├── scenarios/                     # Benchmark Scenarios (50 JSONL files)
│   │   ├── data_integrity/            # Amount mismatch, duplicate rows, missing records
│   │   ├── fault_tolerance/           # Timeouts, rate limits, malformed API payloads
│   │   ├── security_governance/       # Unauthorized actions, prompt injections
│   │   └── exception_handling/        # Conflicting user commands, over-refund attempts
│   ├── adapters/                      # Model Adapters (OpenAI, Anthropic, Ollama, Mock)
│   │   ├── base_adapter.py
│   │   ├── openai_adapter.py
│   │   ├── anthropic_adapter.py
│   │   └── mock_adapter.py
│   ├── evaluator/                     # Metric Calculation & Verification
│   │   ├── metrics.py
│   │   ├── integrity_checker.py
│   │   └── runner.py
│   ├── tests/                         # Pytest Suite
│   ├── pyproject.toml                 # Poetry / Hatch Configuration
│   └── Dockerfile                     # Benchmark Engine Container
├── dashboard/                         # React Frontend Governance & Analytics Dashboard
│   ├── src/
│   │   ├── components/                # Reusable UI Components (Radar charts, Timelines)
│   │   ├── pages/                     # Dashboard, Benchmark Runs, Audit Trace Explorer
│   │   └── services/                  # API Client
│   ├── package.json                   # Node Dependencies
│   └── Dockerfile                     # Nginx React Static Server Container
├── docker-compose.yml                 # Multi-Container Deployment Orchestration
├── README.md                          # Project Overview & Quickstart Guide
├── CONTRIBUTING.md                    # Guidelines for Scenario & Adapter Contributions
├── LICENSE                            # Apache 2.0 Open-Source License
└── CITATION.cff                       # Academic Citation File
```
