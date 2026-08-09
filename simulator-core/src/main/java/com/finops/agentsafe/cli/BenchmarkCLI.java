package com.finops.agentsafe.cli;

import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Command-Line Benchmark Execution Interface.
 *
 * Usage:
 *   java -jar simulator-core.jar --scenario FIN-DATA-002
 *   java -jar simulator-core.jar --category authorization
 *   java -jar simulator-core.jar --all
 */
@Component
public class BenchmarkCLI implements CommandLineRunner {

    private final BenchmarkScenarioLoader scenarioLoader;
    private final BenchmarkRunner benchmarkRunner;

    public BenchmarkCLI(BenchmarkScenarioLoader scenarioLoader, BenchmarkRunner benchmarkRunner) {
        this.scenarioLoader = scenarioLoader;
        this.benchmarkRunner = benchmarkRunner;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args == null || args.length == 0) return;

        Map<String, String> parsedArgs = parseArgs(args);
        if (!parsedArgs.containsKey("scenario") && !parsedArgs.containsKey("category") && !parsedArgs.containsKey("all")) {
            return;
        }

        System.out.println("==================================================");
        System.out.println(" FinOps-AgentSafe Benchmark CLI Engine v0.1.0 ");
        System.out.println("==================================================");

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

        System.out.println("[CLI] Executing " + scenariosToRun.size() + " scenario(s)...");

        List<BenchmarkRunResult> results = new ArrayList<>();
        for (BenchmarkScenario scenario : scenariosToRun) {
            System.out.print("Running " + scenario.getScenarioId() + " (" + scenario.getCategory() + ")... ");
            BenchmarkRunResult res = benchmarkRunner.runScenario(scenario);
            results.add(res);
            System.out.println("DONE -> FARS: " + res.getMetrics().getFarsScore() + " | Integrity: " + res.isFinancialIntegrityPreserved());
        }

        System.out.println("==================================================");
        System.out.println(" Execution Summary ");
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
            }
        }
        return map;
    }
}
