package com.finops.agentsafe.model.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.tool.AgentTool;
import com.finops.agentsafe.tool.AgentToolResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Provider adapter for Groq LLM API integration.
 * Uses Groq OpenAI-compatible Chat Completions endpoint (https://api.groq.com/openai/v1/chat/completions).
 * Disabled unless GROQ_API_KEY environment variable is present.
 */
@Component
public class GroqModelAdapter implements ModelAdapter {

    public static final String PROVIDER_NAME = "groq";
    public static final String ENV_KEY = "GROQ_API_KEY";
    public static final String DEFAULT_MODEL = "openai/gpt-oss-120b";
    public static final String BASE_URL = "https://api.groq.com/openai/v1";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private ProviderPacingController pacingController;

    public GroqModelAdapter() {
        this(System.getenv(ENV_KEY), HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public GroqModelAdapter(String apiKey) {
        this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), new ObjectMapper());
    }

    public GroqModelAdapter(String apiKey, HttpClient httpClient, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.pacingController = ProviderPacingController.groqDefault();
    }

    public GroqModelAdapter(String apiKey, HttpClient httpClient, ObjectMapper objectMapper, ProviderPacingController pacingController) {
        this.apiKey = apiKey;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.pacingController = pacingController;
    }

    public ProviderPacingController getPacingController() { return pacingController; }
    public void setPacingController(ProviderPacingController pacingController) { this.pacingController = pacingController; }

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
        String modelName = (config != null && config.getModelName() != null) ? config.getModelName() : DEFAULT_MODEL;
        return new ModelMetadata(PROVIDER_NAME, modelName, "Groq", "1.0.0", "v1");
    }

    @Override
    public ModelResponse predict(ModelRequest request) {
        ModelMetadata metadata = getMetadata(request != null ? request.getConfiguration() : null);
        ModelUsage usage = new ModelUsage();

        if (!isConfigured()) {
            return ModelResponse.failure(ModelError.notConfigured(PROVIDER_NAME, ENV_KEY), metadata, usage);
        }

        if (pacingController != null) {
            try {
                long pacingWait = pacingController.prepareForRequest(500L); // Estimate ~500 tokens per request
                usage.setPacingWaitMs(pacingWait);
            } catch (ProviderPacingController.ProviderQuotaExceededException e) {
                return ModelResponse.failure(ModelError.rateLimit("Provider quota limit reached: " + e.getMessage()), metadata, usage);
            }
        }

        String endpoint = BASE_URL + "/chat/completions";

        try {
            String jsonPayload = buildGroqRequestBody(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;
            usage.setLatencyMs(latency);

            parseRateLimitHeaders(response.headers(), usage);

            int status = response.statusCode();
            if (status == 401 || status == 403) {
                return ModelResponse.failure(ModelError.authenticationError("Groq API authentication failed (HTTP " + status + ")"), metadata, usage);
            } else if (status == 429) {
                return ModelResponse.failure(ModelError.rateLimit("Groq API rate limit exceeded (HTTP 429)"), metadata, usage);
            } else if (status >= 500) {
                return ModelResponse.failure(ModelError.unavailable("Groq API service error (HTTP " + status + ")"), metadata, usage);
            } else if (status != 200) {
                return ModelResponse.failure(ModelError.providerError("Groq API returned error status HTTP " + status + ": " + response.body(), false), metadata, usage);
            }

            ModelResponse parsedResp = parseGroqResponse(response.body(), metadata, usage);
            if (pacingController != null && parsedResp.getUsage() != null && parsedResp.getUsage().getTotalTokens() != null) {
                pacingController.recordTokensUsed(parsedResp.getUsage().getTotalTokens());
            }
            return parsedResp;
        } catch (java.net.http.HttpTimeoutException e) {
            return ModelResponse.failure(ModelError.timeout("Groq API request timed out: " + e.getMessage()), metadata, usage);
        } catch (Exception e) {
            return ModelResponse.failure(ModelError.providerError("Groq API network/execution error: " + e.getMessage(), true), metadata, usage);
        }
    }

    private void parseRateLimitHeaders(HttpHeaders headers, ModelUsage usage) {
        if (headers == null || usage == null) return;

        // x-ratelimit-limit-requests = RPD Limit
        headers.firstValue("x-ratelimit-limit-requests").ifPresent(v -> {
            try { usage.setLimitRPD(Long.parseLong(v.trim())); } catch (Exception ignored) {}
        });

        // x-ratelimit-remaining-requests = Remaining RPD (Requests Per Day)
        headers.firstValue("x-ratelimit-remaining-requests").ifPresent(v -> {
            try { usage.setRemainingRPD(Long.parseLong(v.trim())); } catch (Exception ignored) {}
        });

        // x-ratelimit-reset-requests = RPD Reset Metadata
        headers.firstValue("x-ratelimit-reset-requests").ifPresent(v -> usage.setResetRPD(v.trim()));

        // x-ratelimit-limit-tokens = TPM Limit
        headers.firstValue("x-ratelimit-limit-tokens").ifPresent(v -> {
            try { usage.setLimitTPM(Long.parseLong(v.trim())); } catch (Exception ignored) {}
        });

        // x-ratelimit-remaining-tokens = Remaining TPM (Tokens Per Minute)
        headers.firstValue("x-ratelimit-remaining-tokens").ifPresent(v -> {
            try { usage.setRemainingTPM(Long.parseLong(v.trim())); } catch (Exception ignored) {}
        });

        // x-ratelimit-reset-tokens = TPM Reset Metadata
        headers.firstValue("x-ratelimit-reset-tokens").ifPresent(v -> usage.setResetTPM(v.trim()));
    }

    public String buildGroqRequestBody(ModelRequest request) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();

        String modelName = request.getConfiguration() != null && request.getConfiguration().getModelName() != null
            ? request.getConfiguration().getModelName()
            : DEFAULT_MODEL;
        body.put("model", modelName);

        if (request.getConfiguration() != null && request.getConfiguration().getTemperature() != null) {
            body.put("temperature", request.getConfiguration().getTemperature());
        } else {
            body.put("temperature", 0.0);
        }

        if (request.getConfiguration() != null && request.getConfiguration().getMaxOutputTokens() != null) {
            body.put("max_tokens", request.getConfiguration().getMaxOutputTokens());
        }

        List<Map<String, Object>> messages = new ArrayList<>();

        // System message
        if (request.getSystemInstruction() != null && !request.getSystemInstruction().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemInstruction()));
        }

        // Scenario user prompt
        String scenarioData = request.getUntrustedScenarioData() != null ? request.getUntrustedScenarioData() : "";
        messages.add(Map.of("role", "user", "content", "Scenario data: " + scenarioData));

        // Subsequent Turns (Previous step tool execution history)
        if (request.getPreviousStepResults() != null) {
            for (AgentToolResult res : request.getPreviousStepResults()) {
                if (res.getToolName() != null) {
                    messages.add(Map.of(
                        "role", "assistant",
                        "content", "Executed tool " + res.getToolName() + " with status " + res.getStatus().name()
                    ));
                    messages.add(Map.of(
                        "role", "user",
                        "content", "Tool " + res.getToolName() + " result: " + (res.getResult() != null ? res.getResult().toString() : res.getStatus().name())
                    ));
                }
            }
        }
        body.put("messages", messages);

        // Available tools in OpenAI format
        if (request.getAvailableTools() != null && !request.getAvailableTools().isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (AgentTool tool : request.getAvailableTools()) {
                tools.add(convertToolToOpenAiFunction(tool));
            }
            body.put("tools", tools);
        }

        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> convertToolToOpenAiFunction(AgentTool tool) {
        Map<String, Object> fn = new HashMap<>();
        fn.put("name", tool.getToolName());
        fn.put("description", tool.getDescription() != null ? tool.getDescription() : tool.getToolName());

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        Map<String, String> inputSchema = tool.getInputSchema();
        if (inputSchema != null) {
            for (Map.Entry<String, String> entry : inputSchema.entrySet()) {
                String paramName = entry.getKey();
                String paramType = entry.getValue();

                Map<String, Object> prop = new HashMap<>();
                prop.put("type", mapJavaTypeToOpenAiType(paramType));
                prop.put("description", paramName + " parameter (" + paramType + ")");
                properties.put(paramName, prop);
                required.add(paramName);
            }
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (!required.isEmpty()) {
            parameters.put("required", required);
        }

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", fn);
        fn.put("parameters", parameters);

        return wrapper;
    }

    private String mapJavaTypeToOpenAiType(String javaType) {
        if (javaType == null) return "string";
        String t = javaType.toUpperCase(Locale.ROOT);
        if (t.contains("INT") || t.contains("LONG")) return "integer";
        if (t.contains("DOUBLE") || t.contains("FLOAT") || t.contains("BIGDECIMAL") || t.contains("NUMBER")) return "number";
        if (t.contains("BOOL")) return "boolean";
        if (t.contains("ARRAY") || t.contains("LIST")) return "array";
        return "string";
    }

    public ModelResponse parseGroqResponse(String responseJson, ModelMetadata metadata, ModelUsage usage) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            // Parse usage
            if (root.has("usage")) {
                JsonNode uNode = root.get("usage");
                long promptTokens = uNode.has("prompt_tokens") ? uNode.get("prompt_tokens").asLong() : 0L;
                long completionTokens = uNode.has("completion_tokens") ? uNode.get("completion_tokens").asLong() : 0L;
                long totalTokens = uNode.has("total_tokens") ? uNode.get("total_tokens").asLong() : promptTokens + completionTokens;

                usage.setInputTokens(promptTokens);
                usage.setOutputTokens(completionTokens);
                usage.setTotalTokens(totalTokens);

                if (uNode.has("completion_tokens_details") && uNode.get("completion_tokens_details").has("reasoning_tokens")) {
                    usage.setThoughtsTokens(uNode.get("completion_tokens_details").get("reasoning_tokens").asLong());
                }
            }

            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return ModelResponse.failure(ModelError.malformed("Groq API returned zero choices in response."), metadata, usage);
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            if (message == null) {
                return ModelResponse.failure(ModelError.malformed("Groq choice missing message object."), metadata, usage);
            }

            // 1. Tool Calls
            if (message.has("tool_calls") && message.get("tool_calls").isArray() && !message.get("tool_calls").isEmpty()) {
                JsonNode tc = message.get("tool_calls").get(0);
                if (tc.has("function")) {
                    JsonNode fn = tc.get("function");
                    String toolName = fn.has("name") ? fn.get("name").asText() : null;
                    if (toolName == null || toolName.isBlank()) {
                        return ModelResponse.failure(ModelError.malformed("Groq tool call missing function name."), metadata, usage);
                    }

                    Map<String, Object> args = new HashMap<>();
                    if (fn.has("arguments")) {
                        String argsText = fn.get("arguments").asText();
                        if (!argsText.isBlank()) {
                            try {
                                JsonNode argsNode = objectMapper.readTree(argsText);
                                if (argsNode.isObject()) {
                                    Iterator<Map.Entry<String, JsonNode>> fields = argsNode.fields();
                                    while (fields.hasNext()) {
                                        var f = fields.next();
                                        if (f.getValue().isTextual()) {
                                            args.put(f.getKey(), f.getValue().asText());
                                        } else if (f.getValue().isBoolean()) {
                                            args.put(f.getKey(), f.getValue().asBoolean());
                                        } else if (f.getValue().isNumber()) {
                                            args.put(f.getKey(), f.getValue().numberValue());
                                        } else {
                                            args.put(f.getKey(), f.getValue().toString());
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                return ModelResponse.failure(ModelError.malformed("Groq tool_calls arguments is not valid JSON: " + e.getMessage()), metadata, usage);
                            }
                        }
                    }

                    AgentDecision decision = AgentDecision.toolCall(toolName, args, "Groq proposed tool call: " + toolName);
                    return ModelResponse.success(decision, metadata, usage, responseJson);
                }
            }

            // 2. Text / Decision content
            if (message.has("content") && !message.get("content").isNull()) {
                String text = message.get("content").asText().trim();
                if (text.isBlank()) {
                    return ModelResponse.failure(ModelError.malformed("Groq response content is blank."), metadata, usage);
                }

                // Check for JSON decision payload
                if (text.startsWith("{") && text.endsWith("}")) {
                    try {
                        JsonNode decNode = objectMapper.readTree(text);
                        if (decNode.has("decisionType")) {
                            String dtStr = decNode.get("decisionType").asText();
                            DecisionType dType = null;
                            try {
                                dType = DecisionType.valueOf(dtStr.toUpperCase(Locale.ROOT));
                            } catch (Exception e) {
                                return ModelResponse.failure(ModelError.malformed("Invalid decisionType in Groq response: " + dtStr), metadata, usage);
                            }

                            String toolName = decNode.has("toolName") ? decNode.get("toolName").asText() : null;
                            String summary = decNode.has("briefReasoningSummary") ? decNode.get("briefReasoningSummary").asText() : text;

                            Map<String, Object> args = new HashMap<>();
                            if (decNode.has("arguments") && decNode.get("arguments").isObject()) {
                                Iterator<Map.Entry<String, JsonNode>> fields = decNode.get("arguments").fields();
                                while (fields.hasNext()) {
                                    var f = fields.next();
                                    args.put(f.getKey(), f.getValue().isTextual() ? f.getValue().asText() : f.getValue().toString());
                                }
                            }

                            if (dType == DecisionType.TOOL_CALL && (toolName == null || toolName.isBlank())) {
                                return ModelResponse.failure(ModelError.malformed("Missing toolName in TOOL_CALL decision."), metadata, usage);
                            }

                            AgentDecision dec = new AgentDecision(dType, toolName, args, summary, 1.0);
                            return ModelResponse.success(dec, metadata, usage, responseJson);
                        }
                    } catch (Exception ignored) {}
                }

                String upperText = text.toUpperCase(Locale.ROOT);
                if (upperText.contains("ESCALATE")) {
                    return ModelResponse.success(AgentDecision.escalate(text, text), metadata, usage, responseJson);
                } else if (upperText.contains("ABSTAIN")) {
                    return ModelResponse.success(AgentDecision.abstain(text), metadata, usage, responseJson);
                } else if (upperText.contains("REQUEST_HUMAN_APPROVAL")) {
                    return ModelResponse.success(AgentDecision.requestApproval("ACTION", text, "TX-0"), metadata, usage, responseJson);
                } else {
                    return ModelResponse.success(AgentDecision.complete(text), metadata, usage, responseJson);
                }
            }

            return ModelResponse.failure(ModelError.malformed("No valid tool_calls or message content in Groq response."), metadata, usage);

        } catch (Exception e) {
            return ModelResponse.failure(ModelError.malformed("Failed to parse Groq response JSON: " + e.getMessage()), metadata, usage);
        }
    }
}
