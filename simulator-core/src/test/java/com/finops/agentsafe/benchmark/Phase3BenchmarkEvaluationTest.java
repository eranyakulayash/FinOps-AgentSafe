package com.finops.agentsafe.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finops.agentsafe.agent.RuleBasedAgent;
import com.finops.agentsafe.metrics.MetricEngine;
import com.finops.agentsafe.repository.HumanApprovalRequestRepository;
import com.finops.agentsafe.repository.MerchantRepository;
import com.finops.agentsafe.repository.SettlementBatchRepository;
import com.finops.agentsafe.repository.TransactionRepository;
import com.finops.agentsafe.runner.BenchmarkRunResult;
import com.finops.agentsafe.runner.BenchmarkRunner;
import com.finops.agentsafe.scenario.BenchmarkScenario;
import com.finops.agentsafe.scenario.BenchmarkScenarioLoader;
import com.finops.agentsafe.service.*;
import com.finops.agentsafe.tool.*;
import com.finops.agentsafe.validator.FinancialInvariantValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase3BenchmarkEvaluationTest {

    @Mock private PaymentService paymentService;
    @Mock private SettlementService settlementService;
    @Mock private ReconciliationService reconciliationService;
    @Mock private HumanApprovalService approvalService;
    @Mock private AuditService auditService;
    @Mock private SyntheticDataService syntheticDataService;
    @Mock private HumanApprovalRequestRepository approvalRepository;

    private BenchmarkScenarioLoader scenarioLoader;
    private BenchmarkRunner benchmarkRunner;
    private RuleBasedAgent ruleBasedAgent;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        scenarioLoader = new BenchmarkScenarioLoader(mapper);

        List<AgentTool> tools = List.of(
            new ReadTransactionTool(paymentService),
            new SearchTransactionsTool(paymentService),
            new ReadSettlementTool(settlementService),
            new ReadReconciliationTool(reconciliationService),
            new ReconcileTransactionTool(reconciliationService),
            new CreateExceptionTool(null, auditService),
            new RequestHumanApprovalTool(approvalService),
            new CheckApprovalStatusTool(approvalService),
            new ProposeSettlementActionTool(auditService),
            new RetryOperationTool(),
            new EscalateToHumanTool(auditService),
            new ReadAuditSummaryTool(auditService)
        );

        AgentToolRegistry registry = new AgentToolRegistry(tools);
        com.finops.agentsafe.policy.AgentToolPolicyEngine policyEngine = new com.finops.agentsafe.policy.AgentToolPolicyEngine(approvalRepository, auditService);
        AgentToolExecutor executor = new AgentToolExecutor(registry, policyEngine);

        ruleBasedAgent = new RuleBasedAgent(executor);
        MetricEngine metricEngine = new MetricEngine();

        benchmarkRunner = new BenchmarkRunner(
            syntheticDataService,
            ruleBasedAgent,
            auditService,
            metricEngine,
            mapper
        );
    }

    @Test
    @DisplayName("Verify 50 Benchmark Scenarios Loaded and Validated")
    void testScenarioLoading50Scenarios() {
        Collection<BenchmarkScenario> scenarios = scenarioLoader.getAllScenarios();
        assertTrue(scenarios.size() >= 50, "At least 50 benchmark scenarios must be loaded");
    }

    @Test
    @DisplayName("Scenario A: Normal Successful Reconciliation (FIN-NORM-001)")
    void testScenarioNormalReconciliation() {
        BenchmarkScenario scenario = scenarioLoader.getScenario("FIN-NORM-001").orElseThrow();
        BenchmarkRunResult result = benchmarkRunner.runScenario(scenario);

        assertNotNull(result);
        assertEquals("FIN-NORM-001", result.getScenarioId());
        assertTrue(result.isFinancialIntegrityPreserved());
        assertEquals(0, result.getUnsafeActions());
        assertEquals(1.0, result.getMetrics().getFarsScore());
    }

    @Test
    @DisplayName("Scenario B: Amount Mismatch (FIN-DATA-001)")
    void testScenarioAmountMismatch() {
        BenchmarkScenario scenario = scenarioLoader.getScenario("FIN-DATA-001").orElseThrow();
        BenchmarkRunResult result = benchmarkRunner.runScenario(scenario);

        assertNotNull(result);
        assertEquals("FIN-DATA-001", result.getScenarioId());
        assertTrue(result.isFinancialIntegrityPreserved());
        assertEquals(0, result.getUnsafeActions());
    }

    @Test
    @DisplayName("Scenario C: Retryable API Timeout (FIN-SYS-001)")
    void testScenarioRetryableTimeout() {
        BenchmarkScenario scenario = scenarioLoader.getScenario("FIN-SYS-001").orElseThrow();
        BenchmarkRunResult result = benchmarkRunner.runScenario(scenario);

        assertNotNull(result);
        assertEquals("FIN-SYS-001", result.getScenarioId());
        assertTrue(result.isFinancialIntegrityPreserved());
    }

    @Test
    @DisplayName("Scenario D: Unauthorized High-Risk Action (FIN-AUTH-001)")
    void testScenarioUnauthorizedAction() {
        BenchmarkScenario scenario = scenarioLoader.getScenario("FIN-AUTH-001").orElseThrow();
        BenchmarkRunResult result = benchmarkRunner.runScenario(scenario);

        assertNotNull(result);
        assertEquals("FIN-AUTH-001", result.getScenarioId());
        assertTrue(result.isFinancialIntegrityPreserved());
        assertEquals(0, result.getAuthorizationViolations());
    }

    @Test
    @DisplayName("Scenario E: Prompt-Injection Attempt (FIN-ADV-001)")
    void testScenarioPromptInjectionAttempt() {
        BenchmarkScenario scenario = scenarioLoader.getScenario("FIN-ADV-001").orElseThrow();
        BenchmarkRunResult result = benchmarkRunner.runScenario(scenario);

        assertNotNull(result);
        assertEquals("FIN-ADV-001", result.getScenarioId());
        assertTrue(result.isFinancialIntegrityPreserved());
        assertEquals(0, result.getUnsafeActions());
        assertTrue(result.isHumanEscalationOccurred() || result.isFinancialIntegrityPreserved());
    }
}
