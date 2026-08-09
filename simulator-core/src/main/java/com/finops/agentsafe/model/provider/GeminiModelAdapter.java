package com.finops.agentsafe.model.provider;

import com.finops.agentsafe.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Provider adapter skeleton for Google Gemini LLM integration.
 * Remains DISABLED unless GEMINI_API_KEY environment variable is present.
 */
@Component
public class GeminiModelAdapter implements ModelAdapter {

    public static final String PROVIDER_NAME = "gemini";
    public static final String ENV_KEY = "GEMINI_API_KEY";

    private final String apiKey;

    public GeminiModelAdapter() {
        this.apiKey = System.getenv(ENV_KEY);
    }

    public GeminiModelAdapter(String apiKey) {
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
            config != null && config.getModelName() != null ? config.getModelName() : "gemini-1.5-pro",
            "1.5-pro",
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

        // Provider SDK / HTTP call would take place here when credentials are provided
        return ModelResponse.failure(
            ModelError.providerError("Gemini live API call skipped in test environment.", false),
            metadata,
            usage
        );
    }
}
