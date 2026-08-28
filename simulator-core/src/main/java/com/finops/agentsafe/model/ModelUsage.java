package com.finops.agentsafe.model;

import java.math.BigDecimal;

/**
 * Token usage, latency, pacing, cost, and rate-limit tracking metadata.
 */
public class ModelUsage {

    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long cachedTokens;
    private Long thoughtsTokens;
    private Long cachedContentTokens;
    private int requestCount;
    private int retryCount;
    private long latencyMs;
    private long pacingWaitMs;
    private BigDecimal estimatedCost;

    // Rate Limit Telemetry (Provider Headers)
    private Long limitRequests;     // x-ratelimit-limit-requests = RPD Limit
    private Long remainingRequests; // x-ratelimit-remaining-requests = Remaining RPD
    private String resetRequests;   // x-ratelimit-reset-requests = RPD Reset
    private Long limitTokens;       // x-ratelimit-limit-tokens = TPM Limit
    private Long remainingTokens;   // x-ratelimit-remaining-tokens = Remaining TPM
    private String resetTokens;     // x-ratelimit-reset-tokens = TPM Reset

    public ModelUsage() {
        this.requestCount = 1;
        this.retryCount = 0;
        this.pacingWaitMs = 0L;
    }

    public ModelUsage(Long inputTokens, Long outputTokens, Long totalTokens, Long cachedTokens, int requestCount, int retryCount, long latencyMs, BigDecimal estimatedCost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cachedTokens = cachedTokens;
        this.requestCount = requestCount;
        this.retryCount = retryCount;
        this.latencyMs = latencyMs;
        this.pacingWaitMs = 0L;
        this.estimatedCost = estimatedCost;
    }

    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }

    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }

    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }

    public Long getCachedTokens() { return cachedTokens; }
    public void setCachedTokens(Long cachedTokens) { this.cachedTokens = cachedTokens; }

    public Long getThoughtsTokens() { return thoughtsTokens; }
    public void setThoughtsTokens(Long thoughtsTokens) { this.thoughtsTokens = thoughtsTokens; }

    public Long getCachedContentTokens() { return cachedContentTokens; }
    public void setCachedContentTokens(Long cachedContentTokens) { this.cachedContentTokens = cachedContentTokens; }

    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public long getPacingWaitMs() { return pacingWaitMs; }
    public void setPacingWaitMs(long pacingWaitMs) { this.pacingWaitMs = pacingWaitMs; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }

    // Rate Limit Telemetry Getters & Setters
    public Long getLimitRequests() { return limitRequests; }
    public void setLimitRequests(Long limitRequests) { this.limitRequests = limitRequests; }
    public Long getLimitRPD() { return limitRequests; }
    public void setLimitRPD(Long limitRPD) { this.limitRequests = limitRPD; }

    public Long getRemainingRequests() { return remainingRequests; }
    public void setRemainingRequests(Long remainingRequests) { this.remainingRequests = remainingRequests; }
    public Long getRemainingRPD() { return remainingRequests; }
    public void setRemainingRPD(Long remainingRPD) { this.remainingRequests = remainingRPD; }

    public String getResetRequests() { return resetRequests; }
    public void setResetRequests(String resetRequests) { this.resetRequests = resetRequests; }
    public String getResetRPD() { return resetRequests; }
    public void setResetRPD(String resetRPD) { this.resetRequests = resetRPD; }

    public Long getLimitTokens() { return limitTokens; }
    public void setLimitTokens(Long limitTokens) { this.limitTokens = limitTokens; }
    public Long getLimitTPM() { return limitTokens; }
    public void setLimitTPM(Long limitTPM) { this.limitTokens = limitTPM; }

    public Long getRemainingTokens() { return remainingTokens; }
    public void setRemainingTokens(Long remainingTokens) { this.remainingTokens = remainingTokens; }
    public Long getRemainingTPM() { return remainingTokens; }
    public void setRemainingTPM(Long remainingTPM) { this.remainingTokens = remainingTPM; }

    public String getResetTokens() { return resetTokens; }
    public void setResetTokens(String resetTokens) { this.resetTokens = resetTokens; }
    public String getResetTPM() { return resetTokens; }
    public void setResetTPM(String resetTPM) { this.resetTokens = resetTPM; }
}
