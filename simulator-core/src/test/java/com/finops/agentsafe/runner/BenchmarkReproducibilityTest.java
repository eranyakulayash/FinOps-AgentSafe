package com.finops.agentsafe.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.RuleBasedAgent;
import com.finops.agentsafe.metrics.MetricEngine;
import com.finops.agentsafe.policy.AgentToolPolicyEngine;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.service.SyntheticDataService;
import com.finops.agentsafe.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BenchmarkReproducibilityTest {

    @Mock
    private SyntheticDataService syntheticDataService;

    @Mock
    private AuditService auditService;

    private BenchmarkRunner benchmarkRunner;

    @BeforeEach
    void setUp() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(new ReadTransactionTool(null)));
        AgentToolPolicyEngine policyEngine = new AgentToolPolicyEngine(null, auditService);
        AgentToolExecutor executor = new AgentToolExecutor(registry, policyEngine);
        RuleBasedAgent agent = new RuleBasedAgent(executor);

        MetricEngine metricEngine = new MetricEngine();
        benchmarkRunner = new BenchmarkRunner(
            syntheticDataService,
            agent,
            auditService,
            metricEngine,
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Identical scenario parameters produce reproducible benchmark run results")
    void testBenchmarkRunReproducibility() {
        BenchmarkScenario scenario = new BenchmarkScenario();
        scenario.setScenarioId("FIN-NORM-001");
        scenario.setVersion("1.0.0");
        scenario.setTitle("Reproducibility Test Scenario");
        scenario.setCategory("NORMAL_OPERATION");
        scenario.setSeed(42L);
        scenario.setMaximumSteps(5);
        scenario.setPermittedTools(Set.of("READ_TRANSACTION", "RECONCILE_TRANSACTION"));

        BenchmarkRunResult run1 = benchmarkRunner.runScenario(scenario, "rule-based-baseline");
        BenchmarkRunResult run2 = benchmarkRunner.runScenario(scenario, "rule-based-baseline");

        assertEquals(run1.getScenarioId(), run2.getScenarioId());
        assertEquals(run1.getScenarioVersion(), run2.getScenarioVersion());
        assertEquals(run1.getSeed(), run2.getSeed());
        assertEquals(run1.getAgent(), run2.getAgent());
        assertEquals(run1.getToolCalls(), run2.getToolCalls());
        assertEquals(run1.getMetrics().getFarsScore(), run2.getMetrics().getFarsScore());
    }
}
