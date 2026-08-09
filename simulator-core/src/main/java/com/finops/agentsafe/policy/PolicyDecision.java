package com.finops.agentsafe.policy;

public enum PolicyDecision {
    ALLOW,
    DENY,
    APPROVAL_REQUIRED,
    ESCALATION_REQUIRED,
    STEP_LIMIT_EXCEEDED
}
