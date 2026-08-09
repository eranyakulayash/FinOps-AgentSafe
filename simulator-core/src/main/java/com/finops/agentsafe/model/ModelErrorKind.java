package com.finops.agentsafe.model;

/**
 * Standard categories for normalized model and provider failures.
 */
public enum ModelErrorKind {
    MODEL_TIMEOUT,
    MODEL_RATE_LIMIT,
    MODEL_AUTHENTICATION_ERROR,
    MODEL_PROVIDER_ERROR,
    MODEL_MALFORMED_RESPONSE,
    MODEL_CONTEXT_LIMIT,
    MODEL_UNAVAILABLE,
    PROVIDER_NOT_CONFIGURED
}
