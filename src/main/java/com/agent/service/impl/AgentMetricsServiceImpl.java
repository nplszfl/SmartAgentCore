package com.agent.service.impl;

import com.agent.dto.AgentMetricsSummary;
import com.agent.entity.AgentMetricsRecord;
import com.agent.service.AgentMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Agent 调用指标服务 - 内存版实现
 *
 * 使用线程安全的有界队列存储最近 N 条记录，避免无限增长。
 * 定期按时间窗口聚合，提供运营分析所需的核心 KPI：
 *  - 成功率 / 超时率
 *  - 平均/最大/最小耗时
 *  - Token 消耗
 *  - 工具 / Agent 使用频次
 */
@Slf4j
@Service
public class AgentMetricsServiceImpl implements AgentMetricsService {

    private static final int DEFAULT_MAX_RECORDS = 10_000;
    private static final int CLEANUP_THRESHOLD = 12_000;

    private final int maxRecords;
    private final Queue<AgentMetricsRecord> records = new ConcurrentLinkedQueue<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    public AgentMetricsServiceImpl() {
        this(DEFAULT_MAX_RECORDS);
    }

    public AgentMetricsServiceImpl(int maxRecords) {
        this.maxRecords = maxRecords;
    }

    @Override
    public void record(AgentMetricsRecord record) {
        if (record == null) return;
        if (record.getId() == null) {
            record.setId(idGenerator.incrementAndGet());
        }
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(LocalDateTime.now());
        }
        records.add(record);
        trim();
    }

    @Override
    public AgentMetricsSummary summarize(LocalDateTime from, LocalDateTime to) {
        List<AgentMetricsRecord> filtered = filterByRange(from, to);

        long total = filtered.size();
        if (total == 0) {
            return AgentMetricsSummary.builder()
                .totalRequests(0)
                .successCount(0)
                .failureCount(0)
                .timeoutCount(0)
                .successRate(0.0)
                .avgDurationMs(0.0)
                .maxDurationMs(0L)
                .minDurationMs(0L)
                .totalInputTokens(0L)
                .totalOutputTokens(0L)
                .totalTokens(0L)
                .avgIterations(0.0)
                .toolUsage(Collections.emptyMap())
                .agentUsage(Collections.emptyMap())
                .topUsers(Collections.emptyList())
                .build();
        }

        long success = filtered.stream().filter(AgentMetricsRecord::isSuccess).count();
        long timeout = filtered.stream().filter(AgentMetricsRecord::isTimeout).count();
        long failure = total - success;

        long max = filtered.stream().mapToLong(AgentMetricsRecord::getDurationMs).max().orElse(0);
        long min = filtered.stream().mapToLong(AgentMetricsRecord::getDurationMs).min().orElse(0);
        double avg = filtered.stream().mapToLong(AgentMetricsRecord::getDurationMs).average().orElse(0);
        double avgIter = filtered.stream().mapToInt(AgentMetricsRecord::getIterations).average().orElse(0);

        long totalIn = filtered.stream().mapToInt(AgentMetricsRecord::getInputTokens).sum();
        long totalOut = filtered.stream().mapToInt(AgentMetricsRecord::getOutputTokens).sum();

        Map<String, Long> toolUsage = new LinkedHashMap<>();
        Map<String, Long> agentUsage = new LinkedHashMap<>();
        Map<String, long[]> userStats = new LinkedHashMap<>();
        for (AgentMetricsRecord r : filtered) {
            incTokenUsage(toolUsage, r.getToolsUsed());
            agentUsage.merge(Optional.ofNullable(r.getAgentName()).orElse("unknown"), 1L, Long::sum);
            if (r.getUserId() != null) {
                userStats.computeIfAbsent(r.getUserId(), k -> new long[2])[0]++;
                userStats.get(r.getUserId())[1] += r.getTotalTokens();
            }
        }

        List<AgentMetricsSummary.TopUser> topUsers = userStats.entrySet().stream()
            .map(e -> AgentMetricsSummary.TopUser.builder()
                .userId(e.getKey())
                .requestCount(e.getValue()[0])
                .totalTokens(e.getValue()[1])
                .build())
            .sorted(Comparator.comparingLong(AgentMetricsSummary.TopUser::getRequestCount).reversed())
            .limit(10)
            .collect(Collectors.toList());

        return AgentMetricsSummary.builder()
            .totalRequests(total)
            .successCount(success)
            .failureCount(failure)
            .timeoutCount(timeout)
            .successRate(total == 0 ? 0.0 : (double) success / total)
            .avgDurationMs(avg)
            .maxDurationMs(max)
            .minDurationMs(min)
            .totalInputTokens(totalIn)
            .totalOutputTokens(totalOut)
            .totalTokens(totalIn + totalOut)
            .avgIterations(avgIter)
            .toolUsage(toolUsage)
            .agentUsage(agentUsage)
            .topUsers(topUsers)
            .build();
    }

    @Override
    public List<AgentMetricsRecord> recent(int limit) {
        return records.stream()
            .sorted(Comparator.comparing(AgentMetricsRecord::getCreatedAt).reversed())
            .limit(Math.max(0, limit))
            .collect(Collectors.toList());
    }

    @Override
    public List<AgentMetricsRecord> recentByUser(String userId, int limit) {
        if (userId == null) return Collections.emptyList();
        return records.stream()
            .filter(r -> userId.equals(r.getUserId()))
            .sorted(Comparator.comparing(AgentMetricsRecord::getCreatedAt).reversed())
            .limit(Math.max(0, limit))
            .collect(Collectors.toList());
    }

    @Override
    public List<AgentMetricsRecord> topFailures(int limit) {
        return records.stream()
            .filter(r -> !r.isSuccess())
            .sorted(Comparator.comparing(AgentMetricsRecord::getCreatedAt).reversed())
            .limit(Math.max(0, limit))
            .collect(Collectors.toList());
    }

    @Override
    public long countAll() {
        return records.size();
    }

    @Override
    public long countBetween(LocalDateTime from, LocalDateTime to) {
        return filterByRange(from, to).size();
    }

    @Override
    public void clear() {
        records.clear();
        log.info("[AgentMetrics] 已清空所有指标记录");
    }

    /**
     * 每天凌晨清理 30 天前的过期数据，避免内存无限增长
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeOldRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        int before = records.size();
        records.removeIf(r -> r.getCreatedAt() != null && r.getCreatedAt().isBefore(cutoff));
        int removed = before - records.size();
        if (removed > 0) {
            log.info("[AgentMetrics] 清理过期记录: 移除 {} 条, 剩余 {} 条", removed, records.size());
        }
    }

    // ============== helpers ==============

    private void trim() {
        if (records.size() <= CLEANUP_THRESHOLD) return;
        List<AgentMetricsRecord> snapshot = new ArrayList<>(records);
        snapshot.sort(Comparator.comparing(AgentMetricsRecord::getCreatedAt));
        int toRemove = snapshot.size() - maxRecords;
        if (toRemove <= 0) return;
        for (int i = 0; i < toRemove; i++) records.remove(snapshot.get(i));
    }

    private List<AgentMetricsRecord> filterByRange(LocalDateTime from, LocalDateTime to) {
        return records.stream()
            .filter(r -> {
                LocalDateTime t = r.getCreatedAt();
                if (t == null) return false;
                if (from != null && t.isBefore(from)) return false;
                return to == null || !t.isAfter(to);
            })
            .collect(Collectors.toList());
    }

    private void incTokenUsage(Map<String, Long> map, String toolsCsv) {
        if (toolsCsv == null || toolsCsv.isBlank()) return;
        for (String t : toolsCsv.split(",")) {
            String name = t.trim();
            if (!name.isEmpty()) map.merge(name, 1L, Long::sum);
        }
    }
}
