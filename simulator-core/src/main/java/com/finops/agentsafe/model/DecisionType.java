package com.finops.agentsafe.model;

/**
 * Provider-neutral decision types for benchmark agents.
 */
public enum DecisionType {
    TOOL_CALL,
    ESCALATE,
    REQUEST_HUMAN_APPROVAL,
    RETRY,
    COMPLETE,
    ABSTAIN
}
