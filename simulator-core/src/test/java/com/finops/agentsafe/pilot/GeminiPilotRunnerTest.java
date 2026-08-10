package com.finops.agentsafe.pilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.model.ModelAdapterRegistry;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import com.finops.agentsafe.tool.AgentToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class GeminiPilotRunnerTest {

    private GeminiPilotRunner pilotRunner;
    private BenchmarkScenarioLoader loader;

    @BeforeEach
    void setUp() {
        loader = new BenchmarkScenarioLoader(new ObjectMapper());
        BenchmarkRunner mockRunner = Mockito.mock(BenchmarkRunner.class);
        LLMBenchmarkAgent mockAgent = Mockito.mock(LLMBenchmarkAgent.class);
        ModelAdapterRegistry mockRegistry = Mockito.mock(ModelAdapterRegistry.class);
        AgentToolExecutor mockExecutor = Mockito.mock(AgentToolExecutor.class);

        pilotRunner = new GeminiPilotRunner(
            loader,
            mockRunner,
            mockAgent,
            mockRegistry,
            mockExecutor,
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("GeminiPilotRunner defines exactly 5 pilot scenario IDs")
    void testPilotScenarioIds() {
        assertEquals(5, GeminiPilotRunner.PILOT_SCENARIO_IDS.size());
        assertTrue(GeminiPilotRunner.PILOT_SCENARIO_IDS.contains("FIN-NORM-001"));
        assertTrue(GeminiPilotRunner.PILOT_SCENARIO_IDS.contains("FIN-DATA-002"));
        assertTrue(GeminiPilotRunner.PILOT_SCENARIO_IDS.contains("FIN-AUTH-001"));
        assertTrue(GeminiPilotRunner.PILOT_SCENARIO_IDS.contains("FIN-ADV-001"));
        assertTrue(GeminiPilotRunner.PILOT_SCENARIO_IDS.contains("FIN-SYS-001"));
    }

    @Test
    @DisplayName("GeminiPilotRunner dry-run mode completes without API calls or exceptions")
    void testDryRun() {
        assertDoesNotThrow(() -> pilotRunner.runPilot("gemini-1.5-flash", true));
    }
}
