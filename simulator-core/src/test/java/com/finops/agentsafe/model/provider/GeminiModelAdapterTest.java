package com.finops.agentsafe.model.provider;

import com.finops.agentsafe.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GeminiModelAdapterTest {

    private GeminiModelAdapter unconfiguredAdapter;

    @BeforeEach
    void setUp() {
        unconfiguredAdapter = new GeminiModelAdapter(null);
    }

    @Test
    @DisplayName("GeminiModelAdapter handles missing API key cleanly without crashing")
    void testUnconfiguredAdapter() {
        assertEquals("gemini", unconfiguredAdapter.getProviderName());
        assertFalse(unconfiguredAdapter.isConfigured());

        ModelMetadata meta = unconfiguredAdapter.getMetadata(new ModelConfiguration());
        assertEquals("gemini", meta.getProvider());

        ModelResponse resp = unconfiguredAdapter.predict(new ModelRequest());
        assertFalse(resp.isSuccess());
        assertEquals(ModelErrorKind.PROVIDER_NOT_CONFIGURED, resp.getError().getKind());
    }

    @Test
    @DisplayName("GeminiModelAdapter uses x-goog-api-key header and omits API key from URL and error messages")
    void testHeaderAuthenticationAndNoKeyInUrl() throws Exception {
        String testApiKey = "SECRET_API_KEY_1234567890";
        HttpClient mockHttpClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);

        when(mockResponse.statusCode()).thenReturn(403);
        when(mockResponse.body()).thenReturn("Forbidden error without disclosing secret");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(mockResponse);

        GeminiModelAdapter adapter = new GeminiModelAdapter(testApiKey, mockHttpClient, new com.fasterxml.jackson.databind.ObjectMapper());
        ModelConfiguration config = new ModelConfiguration("gemini", "gemini-3.6-flash", 0.0, 2048, 10000L, 3, 42L, "financial-agent-system-v1");
        ModelRequest request = new ModelRequest(null, null, null, null, config, 1);

        ModelResponse response = adapter.predict(request);

        assertFalse(response.isSuccess());
        assertFalse(response.getError().getMessage().contains(testApiKey), "API Key must never be included in error messages");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).send(requestCaptor.capture(), any());

        HttpRequest sentRequest = requestCaptor.getValue();
        String uriString = sentRequest.uri().toString();

        assertFalse(uriString.contains("key="), "URL must not contain API key query parameter");
        assertFalse(uriString.contains(testApiKey), "URL must not contain secret API key value");
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent", uriString);

        assertTrue(sentRequest.headers().firstValue("x-goog-api-key").isPresent());
        assertEquals(testApiKey, sentRequest.headers().firstValue("x-goog-api-key").get());
    }

    @Test
    @DisplayName("GeminiModelAdapter parses thoughtsTokenCount and cachedContentTokenCount from usageMetadata")
    void testParseUsageMetadataWithThinkingTokens() {
        GeminiModelAdapter adapter = new GeminiModelAdapter("dummy");
        ModelMetadata meta = new ModelMetadata("gemini", "gemini-3.6-flash", "1.5", "1.0.0", "v1");
        ModelUsage usage = new ModelUsage();

        String json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [ { "text": "COMPLETE task" } ]
                  }
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 50,
                "candidatesTokenCount": 20,
                "totalTokenCount": 170,
                "thoughtsTokenCount": 100,
                "cachedContentTokenCount": 10
              }
            }
            """;

        ModelResponse response = adapter.parseGeminiResponse(json, meta, usage);

        assertTrue(response.isSuccess());
        assertEquals(50L, usage.getInputTokens());
        assertEquals(20L, usage.getOutputTokens());
        assertEquals(170L, usage.getTotalTokens());
        assertEquals(100L, usage.getThoughtsTokens());
        assertEquals(10L, usage.getCachedContentTokens());
    }
}
