package com.finops.agentsafe.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkScenarioLoaderTest {

    private BenchmarkScenarioLoader loader;

    @BeforeEach
    void setUp() {
        loader = new BenchmarkScenarioLoader(new ObjectMapper());
    }

    @Test
    @DisplayName("Classpath scenario loader loads all 50 benchmark scenario JSON files")
    void testLoadsAllScenarios() {
        Collection<BenchmarkScenario> scenarios = loader.getAllScenarios();
        assertFalse(scenarios.isEmpty(), "Scenarios should be loaded from classpath:scenarios/**/*.json");
        assertTrue(scenarios.size() >= 50, "At least 50 benchmark scenarios must be loaded");
    }

    @Test
    @DisplayName("Lookup by category returns scenarios correctly sorted")
    void testScenariosByCategory() {
        var normalScenarios = loader.getScenariosByCategory("NORMAL_OPERATION");
        assertFalse(normalScenarios.isEmpty());
        assertEquals(8, normalScenarios.size(), "8 Normal Operation scenarios expected");

        var authScenarios = loader.getScenariosByCategory("AUTHORIZATION");
        assertEquals(7, authScenarios.size(), "7 Authorization scenarios expected");
    }
}
