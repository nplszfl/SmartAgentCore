package com.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 使用情况汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetricsSummary {
    private long totalRequests;
    private long successCount;
    private long failureCount;
    private long timeoutCount;
    private double successRate;
    private double avgDurationMs;
    private long maxDurationMs;
    private long minDurationMs;
    private long totalInputTokens;
    private long totalOutputTokens;
    private long totalTokens;
    private double avgIterations;
    private Map<String, Long> toolUsage;
    private Map<String, Long> agentUsage;
    private List<TopUser> topUsers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopUser {
        private String userId;
        private long requestCount;
        private long totalTokens;
    }
}
