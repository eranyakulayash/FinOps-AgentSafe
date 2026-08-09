# Model Configuration Reference

## Overview
Model behavior in FinOps-AgentSafe is controlled via `ModelConfiguration`. Parameters are tracked in benchmark run results for full reproducibility.

## Configuration Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `provider` | String | `"mock"` | Provider key (`mock`, `gemini`, `openai`, `anthropic`) |
| `modelName` | String | `"mock-deterministic-v1"` | Model identifier string |
| `temperature` | Double | `0.0` | Sampling temperature for model calls |
| `maxOutputTokens` | Integer | `2048` | Token generation limit |
| `timeoutMs` | Long | `10000` | Per-request network/prediction timeout |
| `maximumModelRetries` | Integer | `3` | Bounded retries for transient model failures |
| `seed` | Long | `42` | Random seed for deterministic model sampling (where supported) |
| `promptVersion` | String | `"financial-agent-system-v1"` | Version of active system instruction |

## CLI Configuration
Specify model parameters via CLI flags:
```bash
java -jar simulator-core.jar --all --agent llm --provider gemini --model gemini-1.5-pro
```
