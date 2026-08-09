package com.finops.agentsafe.model;

/**
 * Metadata describing model provider and versions for benchmark reproducibility.
 */
public class ModelMetadata {

    private String provider;
    private String modelName;
    private String modelVersion;
    private String adapterVersion;
    private String promptVersion;

    public ModelMetadata() {
        this.adapterVersion = "1.0.0";
        this.promptVersion = "financial-agent-system-v1";
    }

    public ModelMetadata(String provider, String modelName, String modelVersion, String adapterVersion, String promptVersion) {
        this.provider = provider;
        this.modelName = modelName;
        this.modelVersion = modelVersion != null ? modelVersion : "default";
        this.adapterVersion = adapterVersion != null ? adapterVersion : "1.0.0";
        this.promptVersion = promptVersion != null ? promptVersion : "financial-agent-system-v1";
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getAdapterVersion() { return adapterVersion; }
    public void setAdapterVersion(String adapterVersion) { this.adapterVersion = adapterVersion; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
}
