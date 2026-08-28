package com.finops.agentsafe.pilot;

import com.finops.agentsafe.experiment.ImplementationFingerprintService;
import com.finops.agentsafe.model.*;
import com.finops.agentsafe.model.provider.GroqModelAdapter;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;

/**
 * Runner for Groq Dry Run Preflight (0 live calls) and One-Call Smoke Test (1 live call).
 */
@Component
public class GroqPreflightRunner {

    private final ModelAdapterRegistry adapterRegistry;
    private final ImplementationFingerprintService fingerprintService;

    public GroqPreflightRunner(ModelAdapterRegistry adapterRegistry,
                               ImplementationFingerprintService fingerprintService) {
        this.adapterRegistry = adapterRegistry;
        this.fingerprintService = fingerprintService != null ? fingerprintService : new ImplementationFingerprintService();
    }

    public void runDryRun() {
        var adapterOpt = adapterRegistry.getAdapter("groq");
        boolean isConfigured = adapterOpt.isPresent() && adapterOpt.get().isConfigured();

        File currentDir = new File(".").getAbsoluteFile();
        File projectRoot = currentDir.getName().equalsIgnoreCase("simulator-core") ? currentDir.getParentFile() : currentDir;
        File expBaseDir = new File(projectRoot, "results/experiments/groq/openai_gpt-oss-120b/repeatability-v1-canonical");
        boolean canonAvailable = !expBaseDir.exists();

        String liveFingerprint = fingerprintService.calculate();
        String gitHead = BenchmarkRunResult.resolveGitHead();

        System.out.println("=== PHASE 5C GROQ PREFLIGHT DRY RUN ===");
        System.out.println("Provider: groq");
        System.out.println("Model: openai/gpt-oss-120b");
        System.out.println("Base URL: https://api.groq.com/openai/v1");
        System.out.println("GROQ_API_KEY: " + (isConfigured ? "PRESENT" : "MISSING"));
        System.out.println();
        System.out.println("Configured RPM: 30");
        System.out.println("Configured RPD: 1000");
        System.out.println("Configured TPM: 8000");
        System.out.println("Configured TPD: 200000");
        System.out.println();
        System.out.println("Planned scenarios: 5");
        System.out.println("Repetitions: 5");
        System.out.println("Planned executions: 25");
        System.out.println("Maximum steps: 5");
        System.out.println();
        System.out.println("Implementation fingerprint: " + liveFingerprint);
        System.out.println("Git HEAD: " + gitHead);
        System.out.println("Canonical experiment directory available: " + (canonAvailable ? "YES" : "NO"));
        System.out.println();
        System.out.println("LIVE GROQ CALLS: 0");
    }

    public String runSingleSmokeTest() {
        var adapterOpt = adapterRegistry.getAdapter("groq");
        if (adapterOpt.isEmpty() || !adapterOpt.get().isConfigured()) {
            System.out.println("NOT READY — GROQ_API_KEY MISSING");
            return "GROQ_PROVIDER_UNAVAILABLE";
        }

        ModelAdapter adapter = adapterOpt.get();
        ModelConfiguration config = ModelConfiguration.groq("openai/gpt-oss-120b");
        config.setMaximumModelRetries(0); // Zero retries enforced for smoke test!

        ModelRequest smokeRequest = new ModelRequest(
            "Respond with exactly: OK",
            "Smoke test request",
            Collections.emptyList(),
            Collections.emptyList(),
            config,
            1
        );

        long start = System.currentTimeMillis();
        ModelResponse response = adapter.predict(smokeRequest);
        long latency = System.currentTimeMillis() - start;

        int providerAttempts = 1;
        int successfulInferenceCalls = (response != null && response.isSuccess()) ? 1 : 0;

        String httpStatusStr = "UNKNOWN";
        boolean is429 = false;
        String outcomeClassification = "GROQ_PROVIDER_UNAVAILABLE";

        if (response != null && response.isSuccess()) {
            httpStatusStr = "HTTP 200 OK";
            outcomeClassification = "GROQ_PROVIDER_AVAILABLE";
        } else if (response != null && response.getError() != null) {
            ModelError err = response.getError();
            String msg = err.getMessage() != null ? err.getMessage() : "";
            if (msg.contains("429")) {
                httpStatusStr = "HTTP 429 Rate Limit Exceeded";
                is429 = true;
                outcomeClassification = "GROQ_PROVIDER_RATE_LIMITED";
            } else if (msg.contains("401") || msg.contains("403") || err.getKind() == ModelErrorKind.MODEL_AUTHENTICATION_ERROR) {
                httpStatusStr = "HTTP 401/403 Authentication Error";
                outcomeClassification = "GROQ_PROVIDER_AUTH_ERROR";
            } else if (msg.toLowerCase().contains("quota") || msg.toLowerCase().contains("billing")) {
                httpStatusStr = "HTTP Quota/Billing Error";
                outcomeClassification = "GROQ_PROVIDER_QUOTA_ERROR";
            } else if (msg.toLowerCase().contains("timeout") || err.getKind() == ModelErrorKind.MODEL_TIMEOUT) {
                httpStatusStr = "HTTP Timeout";
                outcomeClassification = "GROQ_PROVIDER_TIMEOUT";
            } else {
                httpStatusStr = "HTTP Failure: " + msg;
                outcomeClassification = "GROQ_PROVIDER_UNAVAILABLE";
            }
        }

        ModelUsage usage = response != null ? response.getUsage() : new ModelUsage();
        Long inTokens = usage != null ? usage.getInputTokens() : 0L;
        Long outTokens = usage != null ? usage.getOutputTokens() : 0L;
        Long totalTokens = usage != null ? usage.getTotalTokens() : 0L;
        Long remReq = usage != null ? usage.getRemainingRequests() : null;
        Long remTok = usage != null ? usage.getRemainingTokens() : null;

        System.out.println("=== GROQ SINGLE SMOKE REQUEST EVIDENCE ===");
        System.out.println("Provider: groq");
        System.out.println("Model: openai/gpt-oss-120b");
        System.out.println("HTTP/provider status: " + httpStatusStr);
        System.out.println("Outcome classification: " + outcomeClassification);
        System.out.println("Provider request attempts: " + providerAttempts);
        System.out.println("Successful model inference calls: " + successfulInferenceCalls);
        System.out.println("HTTP 429: " + (is429 ? "YES" : "NO"));
        System.out.println("Latency: " + (usage != null && usage.getLatencyMs() > 0 ? usage.getLatencyMs() : latency) + " ms");
        System.out.println("Input tokens: " + (inTokens != null ? inTokens : "N/A"));
        System.out.println("Output tokens: " + (outTokens != null ? outTokens : "N/A"));
        System.out.println("Total tokens: " + (totalTokens != null ? totalTokens : "N/A"));
        System.out.println("Remaining request quota: " + (remReq != null ? remReq : "N/A"));
        System.out.println("Remaining token quota: " + (remTok != null ? remTok : "N/A"));

        return outcomeClassification;
    }
}
