package com.finops.agentsafe.model.provider;

import com.finops.agentsafe.model.*;
import org.springframework.stereotype.Component;

/**
 * Provider adapter skeleton for OpenAI-compatible LLM integration.
 * Remains DISABLED unless OPENAI_API_KEY environment variable is present.
 */
@Component
public class OpenAIModelAdapter implements ModelAdapter {

    public static final String PROVIDER_NAME = "openai";
    public static final String ENV_KEY = "OPENAI_API_KEY";

    private final String apiKey;

    public OpenAIModelAdapter() {
        this.apiKey = System.getenv(ENV_KEY);
    }

    public OpenAIModelAdapter(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public ModelMetadata getMetadata(ModelConfiguration config) {
        return new ModelMetadata(
            PROVIDER_NAME,
            config != null && config.getModelName() != null ? config.getModelName() : "gpt-4o",
            "4o",
            "1.0.0",
            config != null ? config.getPromptVersion() : "financial-agent-system-v1"
        );
    }

    @Override
    public ModelResponse predict(ModelRequest request) {
        ModelMetadata metadata = getMetadata(request.getConfiguration());
        ModelUsage usage = new ModelUsage(0L, 0L, 0L, 0L, 1, 0, 0L, null);

        if (!isConfigured()) {
            return ModelResponse.failure(ModelError.notConfigured(PROVIDER_NAME, ENV_KEY), metadata, usage);
        }

        return ModelResponse.failure(
            ModelError.providerError("OpenAI live API call skipped in test environment.", false),
            metadata,
            usage
        );
    }
}
