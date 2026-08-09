package com.finops.agentsafe.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, validates, and indexes BenchmarkScenario JSON resources.
 */
@Component
public class BenchmarkScenarioLoader {

    private final ObjectMapper objectMapper;
    private final Map<String, BenchmarkScenario> scenarioCache = new ConcurrentHashMap<>();

    public BenchmarkScenarioLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadAllClasspathScenarios();
    }

    public void loadAllClasspathScenarios() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:scenarios/**/*.json");
            for (Resource r : resources) {
                try (InputStream is = r.getInputStream()) {
                    BenchmarkScenario scenario = objectMapper.readValue(is, BenchmarkScenario.class);
                    validateScenario(scenario);
                    scenarioCache.put(scenario.getScenarioId(), scenario);
                } catch (Exception e) {
                    System.err.println("Failed to load scenario resource [" + r.getFilename() + "]: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed scanning scenario resources: " + e.getMessage());
        }
    }

    public void validateScenario(BenchmarkScenario scenario) {
        if (scenario.getScenarioId() == null || scenario.getScenarioId().isBlank()) {
            throw new IllegalArgumentException("Scenario missing scenarioId");
        }
        if (scenario.getCategory() == null || scenario.getCategory().isBlank()) {
            throw new IllegalArgumentException("Scenario [" + scenario.getScenarioId() + "] missing category");
        }
        if (scenario.getMaximumSteps() <= 0) {
            throw new IllegalArgumentException("Scenario [" + scenario.getScenarioId() + "] maximumSteps must be > 0");
        }
    }

    public Optional<BenchmarkScenario> getScenario(String scenarioId) {
        return Optional.ofNullable(scenarioCache.get(scenarioId));
    }

    public List<BenchmarkScenario> getScenariosByCategory(String category) {
        return scenarioCache.values().stream()
            .filter(s -> s.getCategory().equalsIgnoreCase(category))
            .sorted(Comparator.comparing(BenchmarkScenario::getScenarioId))
            .toList();
    }

    public Collection<BenchmarkScenario> getAllScenarios() {
        return scenarioCache.values();
    }
}
