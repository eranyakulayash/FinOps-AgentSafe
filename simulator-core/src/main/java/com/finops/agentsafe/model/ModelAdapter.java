package com.finops.agentsafe.model;

/**
 * Common provider-neutral interface for model integrations.
 * Abstracted from specific LLM SDKs (Gemini, OpenAI, Anthropic, Mock).
 */
public interface ModelAdapter {

    /**
     * Logical provider name (e.g. "mock", "gemini", "openai", "anthropic").
     */
    String getProviderName();

    /**
     * Whether the adapter is configured and ready to accept requests (e.g. API keys present).
     */
    boolean isConfigured();

    /**
     * Submits request to model and returns structured response or normalized error.
     */
    ModelResponse predict(ModelRequest request);

    /**
     * Obtains model metadata for given configuration.
     */
    ModelMetadata getMetadata(ModelConfiguration config);
}
