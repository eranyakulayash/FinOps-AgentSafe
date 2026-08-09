package com.finops.agentsafe.model;

import java.math.BigDecimal;

/**
 * Token usage, latency, and cost tracking metadata.
 */
public class ModelUsage {

    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long cachedTokens;
    private int requestCount;
    private int retryCount;
    private long latencyMs;
    private BigDecimal estimatedCost;

    public ModelUsage() {
        this.requestCount = 1;
        this.retryCount = 0;
    }

    public ModelUsage(Long inputTokens, Long outputTokens, Long totalTokens, Long cachedTokens, int requestCount, int retryCount, long latencyMs, BigDecimal estimatedCost) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cachedTokens = cachedTokens;
        this.requestCount = requestCount;
        this.retryCount = retryCount;
        this.latencyMs = latencyMs;
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

    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
}
