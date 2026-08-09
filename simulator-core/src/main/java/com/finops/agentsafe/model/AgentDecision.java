package com.finops.agentsafe.model;

import java.util.Collections;
import java.util.Map;

/**
 * Strict provider-neutral contract for structured agent decisions.
 * Does not contain or request private chain-of-thought.
 */
public class AgentDecision {

    private DecisionType decisionType;
    private String toolName;
    private Map<String, Object> arguments;
    private String briefReasoningSummary;
    private Double confidence;

    public AgentDecision() {
        this.arguments = Collections.emptyMap();
    }

    public AgentDecision(DecisionType decisionType, String toolName, Map<String, Object> arguments, String briefReasoningSummary, Double confidence) {
        this.decisionType = decisionType;
        this.toolName = toolName;
        this.arguments = arguments != null ? arguments : Collections.emptyMap();
        this.briefReasoningSummary = briefReasoningSummary;
        this.confidence = confidence;
    }

    public static AgentDecision toolCall(String toolName, Map<String, Object> arguments, String briefReasoningSummary) {
        return new AgentDecision(DecisionType.TOOL_CALL, toolName, arguments, briefReasoningSummary, 1.0);
    }

    public static AgentDecision escalate(String reason, String suggestedAction) {
        return new AgentDecision(DecisionType.ESCALATE, "ESCALATE_TO_HUMAN", Map.of("reason", reason, "suggestedAction", suggestedAction), reason, 1.0);
    }

    public static AgentDecision requestApproval(String action, String reason, String txId) {
        return new AgentDecision(DecisionType.REQUEST_HUMAN_APPROVAL, "REQUEST_HUMAN_APPROVAL", Map.of("requestedAction", action, "reason", reason, "relatedTransactionId", txId), reason, 1.0);
    }

    public static AgentDecision complete(String summary) {
        return new AgentDecision(DecisionType.COMPLETE, null, Collections.emptyMap(), summary, 1.0);
    }

    public static AgentDecision abstain(String reason) {
        return new AgentDecision(DecisionType.ABSTAIN, null, Collections.emptyMap(), reason, 0.0);
    }

    public DecisionType getDecisionType() { return decisionType; }
    public void setDecisionType(DecisionType decisionType) { this.decisionType = decisionType; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Map<String, Object> getArguments() { return arguments; }
    public void setArguments(Map<String, Object> arguments) { this.arguments = arguments != null ? arguments : Collections.emptyMap(); }

    public String getBriefReasoningSummary() { return briefReasoningSummary; }
    public void setBriefReasoningSummary(String briefReasoningSummary) { this.briefReasoningSummary = briefReasoningSummary; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}
