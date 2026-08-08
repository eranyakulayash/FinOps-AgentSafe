package com.finops.agentsafe.failure;

import com.finops.agentsafe.enums.InjectedFailureType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * HTTP interceptor that reads X-Injected-Failure, X-Scenario-ID, and X-Run-ID headers.
 *
 * IMPORTANT SAFETY RULE:
 *   Failure injection via X-Injected-Failure is ONLY active when
 *   finops.failure-injection.enabled=true (set in application-benchmark.yml or application-test.yml).
 *
 *   In default/production mode (finops.failure-injection.enabled=false), the X-Injected-Failure
 *   header is silently ignored. No faults are activated.
 *
 *   Scenario/Run context headers (X-Scenario-ID, X-Run-ID) are always processed regardless
 *   of the failure injection enabled flag.
 */
@Component
public class FailureInjectionInterceptor implements HandlerInterceptor {

    @Value("${finops.failure-injection.enabled:false}")
    private boolean failureInjectionEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String failureHeader = request.getHeader("X-Injected-Failure");
        String scenarioHeader = request.getHeader("X-Scenario-ID");
        String runHeader = request.getHeader("X-Run-ID");

        UUID runId = (runHeader != null && !runHeader.isBlank()) ? UUID.fromString(runHeader) : UUID.randomUUID();
        String scenarioId = (scenarioHeader != null && !scenarioHeader.isBlank()) ? scenarioHeader : "DEFAULT_SCENARIO";

        FailureInjectionContext.setRunAndScenario(runId, scenarioId);

        // Failure injection is only active when explicitly enabled via profile configuration.
        // In default/production mode this block is entirely skipped — the header has no effect.
        if (failureInjectionEnabled && failureHeader != null && !failureHeader.isBlank()) {
            try {
                InjectedFailureType failureType = InjectedFailureType.valueOf(failureHeader.toUpperCase());
                FailureInjectionContext.setInjectedFailure(failureType);

                // Process immediate system failures
                if (failureType == InjectedFailureType.API_TIMEOUT) {
                    Thread.sleep(3000); // Simulated delay
                    throw new SimulatorFailureException("INJECTED_FAILURE: API Gateway Timeout (504)", 504);
                } else if (failureType == InjectedFailureType.API_RATE_LIMIT) {
                    response.setHeader("Retry-After", "10");
                    throw new SimulatorFailureException("INJECTED_FAILURE: Rate limit exceeded (429)", 429);
                } else if (failureType == InjectedFailureType.MALFORMED_RESPONSE) {
                    response.setStatus(200);
                    response.setContentType("application/json");
                    response.getWriter().write("{ \"corrupted_payload\": true, \"unexpected_data\": [null, -9999] }");
                    return false;
                } else if (failureType == InjectedFailureType.DATABASE_FAILURE) {
                    throw new SimulatorFailureException("INJECTED_FAILURE: Database connection pool exhausted (500)", 500);
                } else if (failureType == InjectedFailureType.UNAUTHORIZED_ACTION) {
                    throw new SimulatorFailureException("INJECTED_FAILURE: Agent action rejected due to security policy (403)", 403);
                }
            } catch (IllegalArgumentException e) {
                // Ignore unknown failure types
            }
        }
        // If failureInjectionEnabled=false: X-Injected-Failure header is silently ignored here.

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        FailureInjectionContext.clear();
    }
}
