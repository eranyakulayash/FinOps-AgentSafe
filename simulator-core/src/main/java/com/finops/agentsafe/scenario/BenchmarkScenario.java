package com.finops.agentsafe.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Benchmark Scenario model matching Phase 1 & Phase 3 schema.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BenchmarkScenario {

    private String scenarioId;
    private String version;
    private String title;
    private String description;
    private String category;
    private String difficulty;
    private long seed;

    private Map<String, Object> initialState;
    private Set<String> permittedTools;
    private List<String> injectedFailures;
    private List<String> expectedInvariants;
    private List<String> acceptableOutcomes;
    private List<String> prohibitedOutcomes;

    private boolean expectedEscalation;
    private int maximumSteps;
    private Map<String, Object> scoringMetadata;

    public BenchmarkScenario() {}

    public BenchmarkScenario(String scenarioId, String version, String title, String description,
                             String category, String difficulty, long seed, Map<String, Object> initialState,
                             Set<String> permittedTools, List<String> injectedFailures, List<String> expectedInvariants,
                             List<String> acceptableOutcomes, List<String> prohibitedOutcomes,
                             boolean expectedEscalation, int maximumSteps, Map<String, Object> scoringMetadata) {
        this.scenarioId = scenarioId;
        this.version = version;
        this.title = title;
        this.description = description;
        this.category = category;
        this.difficulty = difficulty;
        this.seed = seed;
        this.initialState = initialState;
        this.permittedTools = permittedTools;
        this.injectedFailures = injectedFailures;
        this.expectedInvariants = expectedInvariants;
        this.acceptableOutcomes = acceptableOutcomes;
        this.prohibitedOutcomes = prohibitedOutcomes;
        this.expectedEscalation = expectedEscalation;
        this.maximumSteps = maximumSteps;
        this.scoringMetadata = scoringMetadata;
    }

    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public Map<String, Object> getInitialState() { return initialState; }
    public void setInitialState(Map<String, Object> initialState) { this.initialState = initialState; }

    public Set<String> getPermittedTools() { return permittedTools; }
    public void setPermittedTools(Set<String> permittedTools) { this.permittedTools = permittedTools; }

    public List<String> getInjectedFailures() { return injectedFailures; }
    public void setInjectedFailures(List<String> injectedFailures) { this.injectedFailures = injectedFailures; }

    public List<String> getExpectedInvariants() { return expectedInvariants; }
    public void setExpectedInvariants(List<String> expectedInvariants) { this.expectedInvariants = expectedInvariants; }

    public List<String> getAcceptableOutcomes() { return acceptableOutcomes; }
    public void setAcceptableOutcomes(List<String> acceptableOutcomes) { this.acceptableOutcomes = acceptableOutcomes; }

    public List<String> getProhibitedOutcomes() { return prohibitedOutcomes; }
    public void setProhibitedOutcomes(List<String> prohibitedOutcomes) { this.prohibitedOutcomes = prohibitedOutcomes; }

    public boolean isExpectedEscalation() { return expectedEscalation; }
    public void setExpectedEscalation(boolean expectedEscalation) { this.expectedEscalation = expectedEscalation; }

    public int getMaximumSteps() { return maximumSteps; }
    public void setMaximumSteps(int maximumSteps) { this.maximumSteps = maximumSteps; }

    public Map<String, Object> getScoringMetadata() { return scoringMetadata; }
    public void setScoringMetadata(Map<String, Object> scoringMetadata) { this.scoringMetadata = scoringMetadata; }
}
