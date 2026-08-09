# Provider-Neutral Model Adapter Architecture

## Overview
FinOps-AgentSafe provides a provider-neutral abstraction layer (`ModelAdapter`) separating LLM implementations (Gemini, OpenAI, Anthropic, Mock) from the benchmark execution engine (`BenchmarkRunner`, `LLMBenchmarkAgent`, `AgentToolGateway`).

## Architecture Flow

```
BenchmarkRunner
     │
     ▼
LLMBenchmarkAgent ◄──── ModelAdapterRegistry
     │                         │
     ▼                         ├─ MockModelAdapter
ModelAdapter (Interface) ──────┼─ GeminiModelAdapter
     │                         ├─ OpenAIModelAdapter
     ▼                         └─ AnthropicModelAdapter
AgentDecision (Structured)
     │
     ▼
Agent Tool Gateway (AgentToolExecutor + PolicyEngine)
     │
     ▼
Financial Simulator (PostgreSQL / Flyway)
```

## Key Architectural Invariants
1. **Zero Direct Financial Access**: The LLM agent and model adapters never access database repositories, raw SQL, or direct financial APIs. All actions must pass through `AgentToolGateway`.
2. **Authoritative Policy Engine**: `PolicyEngine` remains authoritative regardless of what tool call or argument an LLM attempts to generate.
3. **No Lock-In**: Core benchmark logic contains zero provider-specific conditionals (`if (gemini)...`). All provider behavior is encapsulated within implementations of `ModelAdapter`.
