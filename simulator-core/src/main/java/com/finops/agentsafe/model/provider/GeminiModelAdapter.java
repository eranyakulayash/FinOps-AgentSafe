package com.finops.agentsafe.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.AgentToolResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Provider adapter for Google Gemini LLM API integration.
 * Uses official Gemini REST API v1beta / v1 generateContent with structured function calling.
 * Remains DISABLED unless GEMINI_API_KEY environment variable is present.
 */
@Component
public class GeminiModelAdapter implements ModelAdapter {

    public static final String PROVIDER_NAME = "gemini";
    public static final String ENV_KEY = "GEMINI_API_KEY";
    private static final String DEFAULT_MODEL = "gemini-3.6-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiModelAdapter() {
        this(System.getenv(ENV_KEY), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public GeminiModelAdapter(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public GeminiModelAdapter(String apiKey, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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
        String modelName = config != null && config.getModelName() != null ? config.getModelName() : DEFAULT_MODEL;
        return new ModelMetadata(
            PROVIDER_NAME,
            modelName,
            "1.5",
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

        String modelName = metadata.getModelName();
        String endpoint = BASE_URL + modelName + ":generateContent";

        try {
            String jsonPayload = buildGeminiRequestBody(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;
            usage.setLatencyMs(latency);

            int status = response.statusCode();
            if (status == 401 || status == 403) {
                return ModelResponse.failure(ModelError.authenticationError("Gemini API authentication failed (HTTP " + status + ")"), metadata, usage);
            } else if (status == 429) {
                return ModelResponse.failure(ModelError.rateLimit("Gemini API rate limit exceeded (HTTP 429)"), metadata, usage);
            } else if (status >= 500) {
                return ModelResponse.failure(ModelError.unavailable("Gemini API service error (HTTP " + status + ")"), metadata, usage);
            } else if (status != 200) {
                return ModelResponse.failure(ModelError.providerError("Gemini API returned error status HTTP " + status + ": " + response.body(), false), metadata, usage);
            }

            return parseGeminiResponse(response.body(), metadata, usage);
        } catch (java.net.http.HttpTimeoutException e) {
            return ModelResponse.failure(ModelError.timeout("Gemini API request timed out: " + e.getMessage()), metadata, usage);
        } catch (Exception e) {
            return ModelResponse.failure(ModelError.providerError("Gemini API network/execution error: " + e.getMessage(), true), metadata, usage);
        }
    }

    public String buildGeminiRequestBody(ModelRequest request) throws Exception {
        Map<String, Object> body = new HashMap<>();

        // System Instruction
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", request.getSystemInstruction()))));
        }

        // Contents (Multi-turn conversation history)
        List<Map<String, Object>> contents = new ArrayList<>();

        // Turn 1: Initial scenario user prompt
        String scenarioData = request.getUntrustedScenarioData() != null ? request.getUntrustedScenarioData() : "";
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", "Scenario data: " + scenarioData))));

        // Subsequent Turns: Previous step tool executions & results
        if (request.getPreviousStepResults() != null) {
            for (AgentToolResult res : request.getPreviousStepResults()) {
                if (res.getToolName() != null) {
                    contents.add(Map.of(
                        "role", "model",
                        "parts", List.of(Map.of("text", "Executed tool " + res.getToolName() + " with status " + res.getStatus().name()))
                    ));
                    contents.add(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", "Tool " + res.getToolName() + " result: " + (res.getResult() != null ? res.getResult().toString() : res.getStatus().name())))
                    ));
                }
            }
        }
        body.put("contents", contents);

        // Permitted Tools -> Gemini Function Declarations
        if (request.getAvailableTools() != null && !request.getAvailableTools().isEmpty()) {
            List<Map<String, Object>> funcDeclarations = new ArrayList<>();
            for (AgentTool tool : request.getAvailableTools()) {
                funcDeclarations.add(convertToolToFunctionDeclaration(tool));
            }
            body.put("tools", List.of(Map.of("functionDeclarations", funcDeclarations)));
        }

        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> convertToolToFunctionDeclaration(AgentTool tool) {
        Map<String, Object> decl = new HashMap<>();
        decl.put("name", tool.getToolName());
        decl.put("description", tool.getDescription() != null ? tool.getDescription() : tool.getToolName());

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        Map<String, String> inputSchema = tool.getInputSchema();
        if (inputSchema != null) {
            for (Map.Entry<String, String> entry : inputSchema.entrySet()) {
                String paramName = entry.getKey();
                String paramType = entry.getValue();

                Map<String, Object> prop = new HashMap<>();
                prop.put("type", mapJavaTypeToGeminiType(paramType));
                prop.put("description", paramName + " parameter (" + paramType + ")");
                properties.put(paramName, prop);
                required.add(paramName);
            }
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }
        decl.put("parameters", parameters);

        return decl;
    }

    private String mapJavaTypeToGeminiType(String javaType) {
        if (javaType == null) return "STRING";
        String t = javaType.toUpperCase(Locale.ROOT);
        if (t.contains("INT") || t.contains("LONG")) return "INTEGER";
        if (t.contains("DOUBLE") || t.contains("FLOAT") || t.contains("BIGDECIMAL") || t.contains("NUMBER")) return "NUMBER";
        if (t.contains("BOOL")) return "BOOLEAN";
        if (t.contains("ARRAY") || t.contains("LIST")) return "ARRAY";
        return "STRING";
    }

    public ModelResponse parseGeminiResponse(String responseJson, ModelMetadata metadata, ModelUsage usage) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            // Parse usage metadata
            if (root.has("usageMetadata")) {
                JsonNode uNode = root.get("usageMetadata");
                long promptTokens = uNode.has("promptTokenCount") ? uNode.get("promptTokenCount").asLong() : 0L;
                long outputTokens = uNode.has("candidatesTokenCount") ? uNode.get("candidatesTokenCount").asLong() : 0L;
                long totalTokens = uNode.has("totalTokenCount") ? uNode.get("totalTokenCount").asLong() : promptTokens + outputTokens;
                usage.setInputTokens(promptTokens);
                usage.setOutputTokens(outputTokens);
                usage.setTotalTokens(totalTokens);

                if (uNode.has("thoughtsTokenCount")) {
                    usage.setThoughtsTokens(uNode.get("thoughtsTokenCount").asLong());
                }
                if (uNode.has("cachedContentTokenCount")) {
                    usage.setCachedContentTokens(uNode.get("cachedContentTokenCount").asLong());
                }
            }

            JsonNode candidates = root.get("candidates");
            if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
                return ModelResponse.failure(ModelError.malformed("Gemini API returned zero candidates in response."), metadata, usage);
            }

            JsonNode firstCand = candidates.get(0);
            JsonNode content = firstCand.get("content");
            if (content == null || !content.has("parts")) {
                return ModelResponse.failure(ModelError.malformed("Gemini candidate content missing parts."), metadata, usage);
            }

            JsonNode parts = content.get("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return ModelResponse.failure(ModelError.malformed("Gemini parts array is empty."), metadata, usage);
            }

            for (JsonNode part : parts) {
                if (part.has("functionCall")) {
                    JsonNode fc = part.get("functionCall");
                    String toolName = fc.get("name").asText();
                    Map<String, Object> args = new HashMap<>();
                    if (fc.has("args")) {
                        Iterator<Map.Entry<String, JsonNode>> fields = fc.get("args").fields();
                        while (fields.hasNext()) {
                            var f = fields.next();
                            args.put(f.getKey(), f.getValue().isTextual() ? f.getValue().asText() : f.getValue().toString());
                        }
                    }
                    AgentDecision decision = AgentDecision.toolCall(toolName, args, "Gemini proposed function call: " + toolName);
                    return ModelResponse.success(decision, metadata, usage, responseJson);
                } else if (part.has("text")) {
                    String text = part.get("text").asText().trim();
                    if (text.toUpperCase(Locale.ROOT).contains("ESCALATE")) {
                        return ModelResponse.success(AgentDecision.escalate(text, text), metadata, usage, responseJson);
                    } else if (text.toUpperCase(Locale.ROOT).contains("ABSTAIN")) {
                        return ModelResponse.success(AgentDecision.abstain(text), metadata, usage, responseJson);
                    } else {
                        return ModelResponse.success(AgentDecision.complete(text), metadata, usage, responseJson);
                    }
                }
            }

            return ModelResponse.failure(ModelError.malformed("No valid functionCall or recognized completion text in Gemini response."), metadata, usage);

        } catch (Exception e) {
            return ModelResponse.failure(ModelError.malformed("Failed to parse Gemini response JSON: " + e.getMessage()), metadata, usage);
        }
    }
}
