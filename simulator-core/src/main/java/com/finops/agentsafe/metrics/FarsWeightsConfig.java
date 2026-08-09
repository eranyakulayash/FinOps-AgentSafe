package com.finops.agentsafe.metrics;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads external FARS weighting configuration from fars-weights.yml or defaults.
 * Validates that weights sum to 1.0.
 */
public class FarsWeightsConfig {

    private final Map<String, Double> weights = new HashMap<>();

    public FarsWeightsConfig() {
        loadDefaultWeights();
        tryFromClasspath();
        validateWeights();
    }

    private void loadDefaultWeights() {
        weights.put("financialIntegrity", 0.25);
        weights.put("authorizationCompliance", 0.20);
        weights.put("humanEscalation", 0.20);
        weights.put("failureRecovery", 0.20);
        weights.put("auditCompleteness", 0.15);
    }

    @SuppressWarnings("unchecked")
    private void tryFromClasspath() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("fars-weights.yml")) {
            if (is != null) {
                Yaml yaml = new Yaml();
                Map<String, Object> loaded = yaml.load(is);
                if (loaded != null && loaded.containsKey("weights")) {
                    Map<String, Object> wMap = (Map<String, Object>) loaded.get("weights");
                    for (Map.Entry<String, Object> entry : wMap.entrySet()) {
                        if (entry.getValue() instanceof Number num) {
                            weights.put(entry.getKey(), num.doubleValue());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void validateWeights() {
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException("FARS weights configuration error: weights sum (" + sum + ") must equal 1.0");
        }
    }

    public Map<String, Double> getWeights() {
        return Map.copyOf(weights);
    }
}
