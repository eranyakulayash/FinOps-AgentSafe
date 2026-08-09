package com.finops.agentsafe.model;

/**
 * Configuration parameters for model requests.
 */
public class ModelConfiguration {

    private String provider = "mock";
    private String modelName = "mock-deterministic-v1";
    private Double temperature = 0.0;
    private Integer maxOutputTokens = 2048;
    private Long timeoutMs = 10000L;
    private Integer maximumModelRetries = 3;
    private Long seed = 42L;
    private String promptVersion = "financial-agent-system-v1";

    public ModelConfiguration() {}

    public ModelConfiguration(String provider, String modelName, Double temperature, Integer maxOutputTokens, Long timeoutMs, Integer maximumModelRetries, Long seed, String promptVersion) {
        if (provider != null) this.provider = provider;
        if (modelName != null) this.modelName = modelName;
        if (temperature != null) this.temperature = temperature;
        if (maxOutputTokens != null) this.maxOutputTokens = maxOutputTokens;
        if (timeoutMs != null) this.timeoutMs = timeoutMs;
        if (maximumModelRetries != null) this.maximumModelRetries = maximumModelRetries;
        if (seed != null) this.seed = seed;
        if (promptVersion != null) this.promptVersion = promptVersion;
    }

    public static ModelConfiguration defaultMock() {
        return new ModelConfiguration("mock", "mock-deterministic-v1", 0.0, 1024, 5000L, 3, 42L, "financial-agent-system-v1");
    }

    public static ModelConfiguration gemini(String modelName) {
        return new ModelConfiguration("gemini", modelName != null ? modelName : "gemini-1.5-pro", 0.0, 2048, 15000L, 3, 42L, "financial-agent-system-v1");
    }

    public static ModelConfiguration openAi(String modelName) {
        return new ModelConfiguration("openai", modelName != null ? modelName : "gpt-4o", 0.0, 2048, 15000L, 3, 42L, "financial-agent-system-v1");
    }

    public static ModelConfiguration anthropic(String modelName) {
        return new ModelConfiguration("anthropic", modelName != null ? modelName : "claude-3-5-sonnet-20241022", 0.0, 2048, 15000L, 3, 42L, "financial-agent-system-v1");
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Integer getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(Integer maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    public Long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(Long timeoutMs) { this.timeoutMs = timeoutMs; }

    public Integer getMaximumModelRetries() { return maximumModelRetries; }
    public void setMaximumModelRetries(Integer maximumModelRetries) { this.maximumModelRetries = maximumModelRetries; }

    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
}
