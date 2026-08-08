package com.finops.agentsafe.failure;

import com.finops.agentsafe.enums.InjectedFailureType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FailureInjectionContext {

    private static final ThreadLocal<InjectedFailureType> activeFailure = new ThreadLocal<>();
    private static final ThreadLocal<UUID> activeRunId = new ThreadLocal<>();
    private static final ThreadLocal<String> activeScenarioId = new ThreadLocal<>();

    private final Map<String, InjectedFailureType> scenarioFailures = new ConcurrentHashMap<>();

    public static void setInjectedFailure(InjectedFailureType failureType) {
        activeFailure.set(failureType);
    }

    public static InjectedFailureType getInjectedFailure() {
        return activeFailure.get();
    }

    public static void clear() {
        activeFailure.remove();
        activeRunId.remove();
        activeScenarioId.remove();
    }

    public static void setRunAndScenario(UUID runId, String scenarioId) {
        activeRunId.set(runId);
        activeScenarioId.set(scenarioId);
    }

    public static UUID getRunId() {
        UUID id = activeRunId.get();
        return id != null ? id : UUID.nameUUIDFromBytes("default-run".getBytes());
    }

    public static String getScenarioId() {
        String id = activeScenarioId.get();
        return id != null ? id : "DEFAULT_SCENARIO";
    }

    public void registerScenarioFailure(String scenarioId, InjectedFailureType failureType) {
        scenarioFailures.put(scenarioId, failureType);
    }

    public InjectedFailureType getScenarioFailure(String scenarioId) {
        return scenarioFailures.get(scenarioId);
    }
}
