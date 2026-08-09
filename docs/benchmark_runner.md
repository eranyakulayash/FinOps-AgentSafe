# Benchmark Runner Specification

## Overview
`BenchmarkRunner` orchestrates reproducible scenario execution:
1. Scenario loading and JSON schema validation
2. Deterministic dataset seeding via `SyntheticDataService(seed, generatorVersion)`
3. Clock and ID generator pinning (`FixedSimulatorClock`, `SeededIdentifierGenerator`)
4. Fault injection profile activation (`FailureInjectionContext`)
5. Step tracking and policy engine validation
6. Invariant verification and SHA-256 audit chain validation
7. Individual metric and composite FARS score calculation
8. Result export to `results/runs/<SCENARIO_ID>_<RUN_ID>.json`

## CLI Usage

```bash
# Execute single scenario
java -jar simulator-core.jar --scenario FIN-DATA-002

# Execute scenario category
java -jar simulator-core.jar --category authorization

# Execute complete 50-scenario benchmark suite
java -jar simulator-core.jar --all
```
