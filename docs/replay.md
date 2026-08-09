# Decision Trace Replay Architecture

## Overview
FinOps-AgentSafe supports 100% deterministic replay of pre-recorded model decision traces (`AgentDecisionTrace`) via `ReplayAgent`.

## Purpose
Separates **Model Behavior** (probabilistic LLM outputs) from **Benchmark Engine Execution** (policy enforcement, state transitions, audit trail hashing, database transactions).

## Usage
```java
AgentDecisionTrace trace = objectMapper.readValue(traceFile, AgentDecisionTrace.class);
ReplayAgent replayAgent = new ReplayAgent(agentToolExecutor, trace);
BenchmarkRunResult result = benchmarkRunner.runScenario(scenario, replayAgent);
```
Replay runs execute entirely through `AgentToolGateway` without making any live HTTP calls to external LLMs.
