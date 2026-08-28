package com.finops.agentsafe.model;

import com.finops.agentsafe.model.mock.MockModelAdapter;
import com.finops.agentsafe.model.provider.AnthropicModelAdapter;
import com.finops.agentsafe.model.provider.GeminiModelAdapter;
import com.finops.agentsafe.model.provider.OpenAIModelAdapter;
import com.finops.agentsafe.model.provider.GroqModelAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelAdapterRegistryTest {

    private ModelAdapterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ModelAdapterRegistry(List.of(
            new MockModelAdapter(),
            new GeminiModelAdapter(""),
            new OpenAIModelAdapter(""),
            new AnthropicModelAdapter(""),
            new GroqModelAdapter("")
        ));
    }

    @Test
    @DisplayName("Registry contains all registered adapters by logical names")
    void testRegistryLookup() {
        assertTrue(registry.hasAdapter("mock"));
        assertTrue(registry.hasAdapter("gemini"));
        assertTrue(registry.hasAdapter("openai"));
        assertTrue(registry.hasAdapter("anthropic"));
        assertTrue(registry.hasAdapter("groq"));

        assertNotNull(registry.getAdapter("MOCK").orElse(null));
        assertNotNull(registry.getAdapter("Gemini").orElse(null));
        assertNotNull(registry.getAdapter("OPENAI").orElse(null));
        assertNotNull(registry.getAdapter("Anthropic").orElse(null));
        assertNotNull(registry.getAdapter("GROQ").orElse(null));
    }

    @Test
    @DisplayName("External provider adapters report not configured when API key is missing")
    void testMissingCredentialsBehavior() {
        assertFalse(registry.getAdapter("gemini").orElseThrow().isConfigured());
        assertFalse(registry.getAdapter("openai").orElseThrow().isConfigured());
        assertFalse(registry.getAdapter("anthropic").orElseThrow().isConfigured());
        assertFalse(registry.getAdapter("groq").orElseThrow().isConfigured());

        ModelResponse resp = registry.getAdapter("groq").orElseThrow().predict(new ModelRequest());
        assertFalse(resp.isSuccess());
        assertEquals(ModelErrorKind.PROVIDER_NOT_CONFIGURED, resp.getError().getKind());
    }

    @Test
    @DisplayName("Mock adapter reports configured by default without credentials")
    void testMockConfigured() {
        assertTrue(registry.getAdapter("mock").orElseThrow().isConfigured());
    }
}
