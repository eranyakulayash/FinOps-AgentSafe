package com.finops.agentsafe.model;

/**
 * Provider-neutral response payload returned by ModelAdapter implementations.
 */
public class ModelResponse {

    private AgentDecision decision;
    private ModelMetadata metadata;
    private ModelUsage usage;
    private ModelError error;
    private String rawModelOutput;

    public ModelResponse() {
        this.metadata = new ModelMetadata();
        this.usage = new ModelUsage();
    }

    public ModelResponse(AgentDecision decision, ModelMetadata metadata, ModelUsage usage, ModelError error, String rawModelOutput) {
        this.decision = decision;
        this.metadata = metadata != null ? metadata : new ModelMetadata();
        this.usage = usage != null ? usage : new ModelUsage();
        this.error = error;
        this.rawModelOutput = rawModelOutput;
    }

    public static ModelResponse success(AgentDecision decision, ModelMetadata metadata, ModelUsage usage, String rawOutput) {
        return new ModelResponse(decision, metadata, usage, null, rawOutput);
    }

    public static ModelResponse failure(ModelError error, ModelMetadata metadata, ModelUsage usage) {
        return new ModelResponse(null, metadata, usage, error, null);
    }

    public boolean isSuccess() {
        return error == null && decision != null;
    }

    public AgentDecision getDecision() { return decision; }
    public void setDecision(AgentDecision decision) { this.decision = decision; }

    public ModelMetadata getMetadata() { return metadata; }
    public void setMetadata(ModelMetadata metadata) { this.metadata = metadata; }

    public ModelUsage getUsage() { return usage; }
    public void setUsage(ModelUsage usage) { this.usage = usage; }

    public ModelError getError() { return error; }
    public void setError(ModelError error) { this.error = error; }

    public String getRawModelOutput() { return rawModelOutput; }
    public void setRawModelOutput(String rawModelOutput) { this.rawModelOutput = rawModelOutput; }
}
