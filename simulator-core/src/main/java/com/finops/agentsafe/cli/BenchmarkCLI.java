package com.finops.agentsafe.cli;

import com.finops.agentsafe.agent.Agent;
import com.finops.agentsafe.agent.LLMBenchmarkAgent;
import com.finops.agentsafe.agent.RuleBasedAgent;
import com.finops.agentsafe.model.ModelAdapterRegistry;
import com.finops.agentsafe.model.ModelConfiguration;
import com.finops.agentsafe.pilot.GeminiPilotRunner;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Command-Line Benchmark Execution Interface.
 * Supports standard benchmark runs as well as controlled provider pilots (--pilot gemini --dry-run).
 */
@Component
public class BenchmarkCLI implements CommandLineRunner {

    private final BenchmarkScenarioLoader scenarioLoader;
    private final BenchmarkRunner benchmarkRunner;
    private final RuleBasedAgent ruleBasedAgent;
    private final LLMBenchmarkAgent llmAgent;
    private final ModelAdapterRegistry adapterRegistry;
    private final GeminiPilotRunner geminiPilotRunner;
    private final com.finops.agentsafe.experiment.RepeatabilityExperimentRunner repeatabilityExperimentRunner;

    private final com.finops.agentsafe.pilot.GroqPreflightRunner groqPreflightRunner;

    public BenchmarkCLI(BenchmarkScenarioLoader scenarioLoader,
                        BenchmarkRunner benchmarkRunner,
                        RuleBasedAgent ruleBasedAgent,
                        LLMBenchmarkAgent llmAgent,
                        ModelAdapterRegistry adapterRegistry,
                        GeminiPilotRunner geminiPilotRunner,
                        com.finops.agentsafe.experiment.RepeatabilityExperimentRunner repeatabilityExperimentRunner,
                        com.finops.agentsafe.pilot.GroqPreflightRunner groqPreflightRunner) {
        this.scenarioLoader = scenarioLoader;
        this.benchmarkRunner = benchmarkRunner;
        this.ruleBasedAgent = ruleBasedAgent;
        this.llmAgent = llmAgent;
        this.adapterRegistry = adapterRegistry;
        this.geminiPilotRunner = geminiPilotRunner;
        this.repeatabilityExperimentRunner = repeatabilityExperimentRunner;
        this.groqPreflightRunner = groqPreflightRunner;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args == null || args.length == 0) return;

        Map<String, String> parsedArgs = parseArgs(args);

        // Check for --smoke-test command
        if (parsedArgs.containsKey("smoke-test") || parsedArgs.containsKey("smoke")) {
            String smokeProvider = parsedArgs.getOrDefault("smoke-test", parsedArgs.get("smoke"));
            if ("groq".equalsIgnoreCase(smokeProvider)) {
                groqPreflightRunner.runSingleSmokeTest();
                return;
            }
        }

        // Check for --experiment command
        if (parsedArgs.containsKey("experiment")) {
            String expName = parsedArgs.get("experiment");
            boolean dryRun = parsedArgs.containsKey("dry-run");
            String model = parsedArgs.get("model");
            if ("repeatability".equalsIgnoreCase(expName) || "gemini".equalsIgnoreCase(expName)) {
                repeatabilityExperimentRunner.runExperiment(model, dryRun);
                return;
            } else if ("groq".equalsIgnoreCase(expName)) {
                if (dryRun) {
                    groqPreflightRunner.runDryRun();
                } else {
                    groqPreflightRunner.runSingleSmokeTest();
                }
                return;
            } else {
                System.out.println("[CLI ERROR] Unsupported experiment: " + expName);
                return;
            }
        }

        // Check for --pilot command
        if (parsedArgs.containsKey("pilot")) {
            String pilotProvider = parsedArgs.get("pilot");
            boolean dryRun = parsedArgs.containsKey("dry-run");
            String model = parsedArgs.get("model");
            if ("gemini".equalsIgnoreCase(pilotProvider)) {
                geminiPilotRunner.runPilot(model, dryRun, parsedArgs.get("scenario"));
                return;
            } else if ("groq".equalsIgnoreCase(pilotProvider)) {
                if (dryRun) {
                    groqPreflightRunner.runDryRun();
                } else {
                    groqPreflightRunner.runSingleSmokeTest();
                }
                return;
            } else {
                System.out.println("[CLI ERROR] Unsupported pilot provider: " + pilotProvider);
                return;
            }
        }

        if (!parsedArgs.containsKey("scenario") && !parsedArgs.containsKey("category") && !parsedArgs.containsKey("all")) {
            return;
        }

        System.out.println("==================================================");
        System.out.println(" FinOps-AgentSafe Benchmark CLI Engine v0.1.0 ");
        System.out.println("==================================================");

        String agentType = parsedArgs.getOrDefault("agent", "rule-based").toLowerCase(Locale.ROOT);
        String provider = parsedArgs.getOrDefault("provider", "mock").toLowerCase(Locale.ROOT);
        String modelName = parsedArgs.get("model");

        Agent selectedAgent = ruleBasedAgent;

        if ("llm".equals(agentType) || "mock".equals(agentType)) {
            String selectedProvider = "mock".equals(agentType) ? "mock" : provider;
            var adapterOpt = adapterRegistry.getAdapter(selectedProvider);

            if (adapterOpt.isEmpty() || !adapterOpt.get().isConfigured()) {
                System.out.println("[CLI ERROR] PROVIDER_NOT_CONFIGURED: Model provider '" + selectedProvider + "' is not configured or missing environment API key.");
                System.out.println("[CLI INFO] Available configured adapters: " + adapterRegistry.getAllAdapters().stream().filter(a -> a.isConfigured()).map(a -> a.getProviderName()).toList());
                return;
            }

            ModelConfiguration config = new ModelConfiguration(
                selectedProvider,
                modelName != null ? modelName : ("mock".equals(selectedProvider) ? "mock-deterministic-v1" : selectedProvider + "-default"),
                0.0, 2048, 10000L, 3, 42L, "financial-agent-system-v1"
            );
            llmAgent.setModelConfiguration(config);
            selectedAgent = llmAgent;
        }

        List<BenchmarkScenario> scenariosToRun = new ArrayList<>();

        if (parsedArgs.containsKey("scenario")) {
            String scenarioId = parsedArgs.get("scenario");
            scenarioLoader.getScenario(scenarioId).ifPresent(scenariosToRun::add);
        } else if (parsedArgs.containsKey("category")) {
            String cat = parsedArgs.get("category");
            scenariosToRun.addAll(scenarioLoader.getScenariosByCategory(cat));
        } else if (parsedArgs.containsKey("all")) {
            scenariosToRun.addAll(scenarioLoader.getAllScenarios());
        }

        if (scenariosToRun.isEmpty()) {
            System.out.println("[CLI] No matching scenarios found to execute.");
            return;
        }

        System.out.println("[CLI] Executing " + scenariosToRun.size() + " scenario(s) using agent [" + selectedAgent.getAgentId() + "]...");

        List<BenchmarkRunResult> results = new ArrayList<>();
        for (BenchmarkScenario scenario : scenariosToRun) {
            System.out.print("Running " + scenario.getScenarioId() + " (" + scenario.getCategory() + ")... ");
            BenchmarkRunResult res = benchmarkRunner.runScenario(scenario, selectedAgent);
            results.add(res);
            System.out.println("DONE -> FARS: " + res.getMetrics().getFarsScore() + " | Integrity: " + res.isFinancialIntegrityPreserved());
        }

        System.out.println("==================================================");
        System.out.println(" Execution Summary ");
        System.out.println(" Agent: " + selectedAgent.getAgentId());
        System.out.println(" Total Scenarios Executed: " + results.size());
        double avgFars = results.stream().mapToDouble(r -> r.getMetrics().getFarsScore()).average().orElse(0.0);
        System.out.println(String.format(" Average Composite FARS Score: %.3f", avgFars));
        System.out.println("==================================================");
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if ("--scenario".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("scenario", args[i + 1]);
            } else if ("--category".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("category", args[i + 1]);
            } else if ("--all".equalsIgnoreCase(args[i])) {
                map.put("all", "true");
            } else if ("--agent".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("agent", args[i + 1]);
            } else if ("--provider".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("provider", args[i + 1]);
            } else if ("--model".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("model", args[i + 1]);
            } else if ("--pilot".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("pilot", args[i + 1]);
            } else if ("--experiment".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                map.put("experiment", args[i + 1]);
            } else if (("--smoke-test".equalsIgnoreCase(args[i]) || "--smoke".equalsIgnoreCase(args[i])) && i + 1 < args.length) {
                map.put("smoke-test", args[i + 1]);
            } else if ("--dry-run".equalsIgnoreCase(args[i])) {
                map.put("dry-run", "true");
            }
        }
        return map;
    }
}
