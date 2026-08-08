package com.finops.agentsafe.context;

import java.util.UUID;

/**
 * First-class execution context for benchmark and scenario runs.
 * Associates all financial objects (transactions, refunds, reversals, chargebacks,
 * reconciliation, settlements, exceptions, approvals, audit events) with a specific
 * scenario and run.
 *
 * Standalone (non-benchmark) mode uses default values and remains fully functional.
 *
 * Fields:
 *   scenarioId       — logical scenario identifier (e.g. "RECONCILIATION_DISCREPANCY_001")
 *   scenarioVersion  — version string for scenario schema (e.g. "1.0")
 *   runId            — unique UUID per benchmark run execution
 *   seed             — optional long seed for deterministic ID generation
 *   generatorVersion — version of the IdentifierGenerator in use
 */
public class BenchmarkExecutionContext {

    public static final String DEFAULT_SCENARIO_ID = "DEFAULT_SCENARIO";
    public static final String DEFAULT_SCENARIO_VERSION = "1.0";
    public static final UUID DEFAULT_RUN_ID = UUID.nameUUIDFromBytes("default-run".getBytes());
    public static final String DEFAULT_GENERATOR_VERSION = "RANDOM";

    private final String scenarioId;
    private final String scenarioVersion;
    private final UUID runId;
    private final Long seed;
    private final String generatorVersion;

    private BenchmarkExecutionContext(Builder builder) {
        this.scenarioId = builder.scenarioId;
        this.scenarioVersion = builder.scenarioVersion;
        this.runId = builder.runId;
        this.seed = builder.seed;
        this.generatorVersion = builder.generatorVersion;
    }

    public static BenchmarkExecutionContext defaults() {
        return new Builder().build();
    }

    public String getScenarioId() { return scenarioId; }
    public String getScenarioVersion() { return scenarioVersion; }
    public UUID getRunId() { return runId; }
    public Long getSeed() { return seed; }
    public String getGeneratorVersion() { return generatorVersion; }

    public boolean isBenchmarkMode() {
        return seed != null;
    }

    public static class Builder {
        private String scenarioId = DEFAULT_SCENARIO_ID;
        private String scenarioVersion = DEFAULT_SCENARIO_VERSION;
        private UUID runId = DEFAULT_RUN_ID;
        private Long seed = null;
        private String generatorVersion = DEFAULT_GENERATOR_VERSION;

        public Builder scenarioId(String scenarioId) { this.scenarioId = scenarioId; return this; }
        public Builder scenarioVersion(String scenarioVersion) { this.scenarioVersion = scenarioVersion; return this; }
        public Builder runId(UUID runId) { this.runId = runId; return this; }
        public Builder seed(Long seed) { this.seed = seed; return this; }
        public Builder generatorVersion(String generatorVersion) { this.generatorVersion = generatorVersion; return this; }

        public BenchmarkExecutionContext build() {
            return new BenchmarkExecutionContext(this);
        }
    }
}
