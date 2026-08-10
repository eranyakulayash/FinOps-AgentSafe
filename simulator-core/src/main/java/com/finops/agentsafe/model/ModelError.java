package com.finops.agentsafe.model;

/**
 * Normalized model error container.
 */
public class ModelError {

    private ModelErrorKind kind;
    private String message;
    private boolean retryable;
    private String rawErrorDetails;

    public ModelError() {}

    public ModelError(ModelErrorKind kind, String message, boolean retryable, String rawErrorDetails) {
        this.kind = kind;
        this.message = message;
        this.retryable = retryable;
        this.rawErrorDetails = rawErrorDetails;
    }

    public static ModelError notConfigured(String providerName, String envVar) {
        return new ModelError(
            ModelErrorKind.PROVIDER_NOT_CONFIGURED,
            "Provider " + providerName + " is not configured. Missing environment variable: " + envVar,
            false,
            "PROVIDER_NOT_CONFIGURED"
        );
    }

    public static ModelError timeout(String details) {
        return new ModelError(ModelErrorKind.MODEL_TIMEOUT, "Model call timed out: " + details, true, details);
    }

    public static ModelError rateLimit(String details) {
        return new ModelError(ModelErrorKind.MODEL_RATE_LIMIT, "Model rate limit exceeded: " + details, true, details);
    }

    public static ModelError malformed(String details) {
        return new ModelError(ModelErrorKind.MODEL_MALFORMED_RESPONSE, "Malformed response from model: " + details, false, details);
    }

    public static ModelError authenticationError(String details) {
        return new ModelError(ModelErrorKind.MODEL_AUTHENTICATION_ERROR, "Authentication failed: " + details, false, details);
    }

    public static ModelError unavailable(String details) {
        return new ModelError(ModelErrorKind.MODEL_UNAVAILABLE, "Service unavailable: " + details, true, details);
    }

    public static ModelError providerError(String details, boolean retryable) {
        return new ModelError(ModelErrorKind.MODEL_PROVIDER_ERROR, "Provider error: " + details, retryable, details);
    }

    public ModelErrorKind getKind() { return kind; }
    public void setKind(ModelErrorKind kind) { this.kind = kind; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean retryable) { this.retryable = retryable; }

    public String getRawErrorDetails() { return rawErrorDetails; }
    public void setRawErrorDetails(String rawErrorDetails) { this.rawErrorDetails = rawErrorDetails; }
}
