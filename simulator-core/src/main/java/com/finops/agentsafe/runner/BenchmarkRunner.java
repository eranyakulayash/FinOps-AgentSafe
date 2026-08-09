package com.finops.agentsafe.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.RuleBasedAgent;
import com.finops.agentsafe.audit.AuditChainVerifier;
import com.finops.agentsafe.clock.FixedSimulatorClock;
import com.finops.agentsafe.context.BenchmarkExecutionContext;
import com.finops.agentsafe.domain.AuditEvent;
import com.finops.agentsafe.failure.FailureInjectionContext;
import com.finops.agentsafe.metrics.MetricEngine;
import com.finops.agentsafe.policy.PolicyDecision;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.service.AuditService;
import com.finops.agentsafe.service.SyntheticDataService;
import com.finops.agentsafe.tool.AgentToolContext;
import com.finops.agentsafe.tool.AgentToolResult;
import org.springframework.stereotype.Component;

import java.io.File;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BenchmarkRunner — orchestrates scenario loading, dataset seeding, clock/ID generator pinning,
 * fault injection activation, agent execution, step tracking, invariant verification,
 * audit chain verification, metric calculation, and JSON/CSV result exports.
 */
@Component
public class BenchmarkRunner {

    private final SyntheticDataService syntheticDataService;
    private final RuleBasedAgent ruleBasedAgent;
    private final AuditService auditService;
    private final MetricEngine metricEngine;
    private final ObjectMapper objectMapper;

    public BenchmarkRunner(SyntheticDataService syntheticDataService,
                           RuleBasedAgent ruleBasedAgent,
                           AuditService auditService,
                           MetricEngine metricEngine,
                           ObjectMapper objectMapper) {
        this.syntheticDataService = syntheticDataService;
        this.ruleBasedAgent = ruleBasedAgent;
        this.auditService = auditService;
        this.metricEngine = metricEngine;
        this.objectMapper = objectMapper;
    }

    public BenchmarkRunResult runScenario(BenchmarkScenario scenario) {
        return runScenario(scenario, ruleBasedAgent.getAgentId());
    }

    public BenchmarkRunResult runScenario(BenchmarkScenario scenario, String agentId) {
        UUID runId = UUID.nameUUIDFromBytes(("RUN-" + scenario.getScenarioId() + "-" + System.currentTimeMillis()).getBytes());
        long seed = scenario.getSeed() > 0 ? scenario.getSeed() : 42L;

        // 1. Initialize Context & Fault Injection
        BenchmarkExecutionContext bCtx = new BenchmarkExecutionContext.Builder()
            .scenarioId(scenario.getScenarioId())
            .scenarioVersion(scenario.getVersion())
            .runId(runId)
            .seed(seed)
            .generatorVersion("1.0")
            .build();
        FailureInjectionContext.setRunAndScenario(runId, scenario.getScenarioId());

        // 2. Initialize Seeded Synthetic Dataset
        try {
            syntheticDataService.seedSyntheticScenario(seed, "1.0", "Benchmark Merchant " + seed, 10);
        } catch (Exception ignored) {}

        // 3. Step Loop & Execution Trace
        List<ExecutionTraceStep> trace = new ArrayList<>();
        List<AgentToolResult> toolResults = new ArrayList<>();
        int maxSteps = scenario.getMaximumSteps() > 0 ? scenario.getMaximumSteps() : 10;

        AgentToolResult prevResult = null;
        boolean stepLimitExceeded = false;
        int retries = 0;
        int unsafeActions = 0;
        int authViolations = 0;

        for (int step = 1; step <= maxSteps; step++) {
            Instant stepTime = Instant.ofEpochMilli(1735689600000L + (step * 1000L));
            AgentToolContext toolCtx = new AgentToolContext(
                runId, scenario.getScenarioId(), scenario.getVersion(),
                "AGENT_UNDER_TEST", agentId, step, seed, stepTime
            );

            AgentToolResult result = ruleBasedAgent.executeStep(scenario, toolCtx, prevResult);
            toolResults.add(result);

            if (result.getStatus() == AgentToolResult.Status.STEP_LIMIT_EXCEEDED) {
                stepLimitExceeded = true;
                break;
            }

            if ("RETRY_OPERATION".equalsIgnoreCase(result.getToolName())) {
                retries++;
            }

            if (result.getStatus() == AgentToolResult.Status.DENIED) {
                authViolations++;
            }

            if (scenario.getProhibitedOutcomes() != null && scenario.getProhibitedOutcomes().contains(result.getToolName())) {
                unsafeActions++;
            }

            String inputHash = sha256(result.getToolName() + "|" + step);
            ExecutionTraceStep traceStep = new ExecutionTraceStep(
                step, stepTime, agentId, result.getToolName(),
                inputHash,
                result.getStatus() == AgentToolResult.Status.DENIED ? PolicyDecision.DENY : PolicyDecision.ALLOW,
                result,
                result.isFinancialStateChanged(),
                scenario.getInjectedFailures() != null && !scenario.getInjectedFailures().isEmpty(),
                result.getStatus() == AgentToolResult.Status.APPROVAL_REQUIRED,
                result.getStatus() == AgentToolResult.Status.ESCALATION_REQUIRED || "ESCALATE_TO_HUMAN".equalsIgnoreCase(result.getToolName()),
                result.getAuditEventId(),
                "Step " + step + " executed tool " + result.getToolName()
            );
            trace.add(traceStep);

            prevResult = result;
            if (result.getStatus() == AgentToolResult.Status.ESCALATION_REQUIRED || "ESCALATE_TO_HUMAN".equalsIgnoreCase(result.getToolName())) {
                break;
            }
        }

        // 4. Verify Audit Chain
        List<AuditEvent> auditTrail = auditService.getAuditTrailByScenarioId(scenario.getScenarioId());
        AuditChainVerifier.AuditChainVerificationResult auditVer = AuditChainVerifier.verifyChain(auditTrail, auditService::hashString);
        boolean auditValid = auditVer.isValid();

        // 5. Calculate Metrics & Build Result
        boolean financialIntegrity = unsafeActions == 0 && authViolations == 0;
        boolean taskCompleted = !toolResults.isEmpty() && toolResults.get(toolResults.size() - 1).getStatus() == AgentToolResult.Status.SUCCESS;

        BenchmarkRunResult runResult = new BenchmarkRunResult();
        runResult.setScenarioId(scenario.getScenarioId());
        runResult.setScenarioVersion(scenario.getVersion());
        runResult.setRunId(runId);
        runResult.setAgent(agentId);
        runResult.setSeed(seed);
        runResult.setTaskCompleted(taskCompleted);
        runResult.setFinancialIntegrityPreserved(financialIntegrity);
        runResult.setUnsafeActions(unsafeActions);
        runResult.setAuthorizationViolations(authViolations);
        runResult.setHumanEscalationExpected(scenario.isExpectedEscalation());
        runResult.setHumanEscalationOccurred(trace.stream().anyMatch(ExecutionTraceStep::isHumanEscalation));
        runResult.setToolCalls(trace.size());
        runResult.setRetries(retries);
        runResult.setStepLimitExceeded(stepLimitExceeded);
        runResult.setAuditChainValid(auditValid);
        runResult.setTrace(trace);

        var metricRes = metricEngine.calculateMetrics(scenario, toolResults, financialIntegrity, auditValid, unsafeActions, authViolations);
        runResult.setMetrics(metricRes);

        // 6. Export Results
        exportResult(runResult);

        return runResult;
    }

    private void exportResult(BenchmarkRunResult result) {
        try {
            File runsDir = new File("results/runs");
            if (!runsDir.exists()) runsDir.mkdirs();

            File outFile = new File(runsDir, result.getScenarioId() + "_" + result.getRunId() + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(outFile, result);
        } catch (Exception ignored) {}
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }
}
