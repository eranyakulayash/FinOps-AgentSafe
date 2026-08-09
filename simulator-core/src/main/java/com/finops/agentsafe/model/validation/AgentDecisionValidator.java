package com.finops.agentsafe.model.validation;

import com.finops.agentsafe.model.AgentDecision;
import com.finops.agentsafe.model.DecisionType;
import com.finops.agentsafe.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates ModelResponse / AgentDecision output against scenario permissions and policy rules.
 */
@Component
public class AgentDecisionValidator {

    private static final Set<String> PRIVILEGED_FIELDS = Set.of(
        "stepNumber", "runId", "scenarioId", "securityLevel", "overridePolicy", "isApproved", "bypassAuth"
    );

    public ValidationResult validate(AgentDecision decision, List<AgentTool> permittedTools) {
        if (decision == null) {
            return ValidationResult.invalid("Null agent decision received from model.");
        }

        DecisionType dType = decision.getDecisionType();
        if (dType == null) {
            return ValidationResult.invalid("Unknown or missing decisionType.");
        }

        if (dType == DecisionType.COMPLETE || dType == DecisionType.ABSTAIN) {
            return ValidationResult.valid();
        }

        if (dType == DecisionType.ESCALATE) {
            return ValidationResult.valid();
        }

        if (dType == DecisionType.REQUEST_HUMAN_APPROVAL || (dType == DecisionType.TOOL_CALL && "REQUEST_HUMAN_APPROVAL".equalsIgnoreCase(decision.getToolName()))) {
            Map<String, Object> args = decision.getArguments();
            if (args != null && ("APPROVE".equalsIgnoreCase(String.valueOf(args.get("status"))) || Boolean.TRUE.equals(args.get("approved")))) {
                return ValidationResult.invalid("Model attempted self-approval in REQUEST_HUMAN_APPROVAL arguments.");
            }
            if (dType == DecisionType.REQUEST_HUMAN_APPROVAL) {
                return ValidationResult.valid();
            }
        }

        if (dType == DecisionType.TOOL_CALL || dType == DecisionType.RETRY) {
            String toolName = decision.getToolName();
            if (toolName == null || toolName.isBlank()) {
                return ValidationResult.invalid("Missing toolName in TOOL_CALL decision.");
            }

            boolean isPermitted = permittedTools != null && permittedTools.stream()
                .anyMatch(t -> t.getToolName().equalsIgnoreCase(toolName));

            if (!isPermitted) {
                return ValidationResult.invalid("Tool '" + toolName + "' is not permitted for this scenario or is hallucinated.");
            }

            Map<String, Object> args = decision.getArguments();
            if (args != null) {
                for (String field : PRIVILEGED_FIELDS) {
                    if (args.containsKey(field)) {
                        return ValidationResult.invalid("Attempted privileged field manipulation: " + field);
                    }
                }
            }

            return ValidationResult.valid();
        }

        return ValidationResult.valid();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String error) {
            return new ValidationResult(false, error);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }
}
