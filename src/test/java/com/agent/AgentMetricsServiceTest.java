package com.agent;

import com.agent.dto.AgentMetricsSummary;
import com.agent.entity.AgentMetricsRecord;
import com.agent.service.impl.AgentMetricsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentMetricsService 业务功能测试
 */
class AgentMetricsServiceTest {

    private AgentMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentMetricsServiceImpl(1000);
    }

    @Test
    @DisplayName("记录并汇总调用指标")
    void recordAndSummarize() {
        // 3 次成功、1 次失败、1 次超时
        service.record(build("u1", "general-assistant", true, false, 100, 2, List.of("search")));
        service.record(build("u1", "general-assistant", true, false, 200, 3, List.of("calculator")));
        service.record(build("u2", "sql-expert", true, false, 300, 1, List.of()));
        service.record(build("u2", "sql-expert", false, false, 50, 0, List.of(), "parse error"));
        service.record(build("u3", "general-assistant", false, true, 5000, 10, List.of("search")));

        AgentMetricsSummary summary = service.summarize(null, LocalDateTime.now().plusMinutes(1));

        assertEquals(5, summary.getTotalRequests());
        assertEquals(3, summary.getSuccessCount());
        assertEquals(2, summary.getFailureCount());
        assertEquals(1, summary.getTimeoutCount());
        assertEquals(0.6, summary.getSuccessRate(), 0.001);
        assertTrue(summary.getAvgDurationMs() > 0);
        assertEquals(5000, summary.getMaxDurationMs());
        assertEquals(50, summary.getMinDurationMs());
        assertEquals(2, summary.getToolUsage().get("search"));
        assertEquals(1, summary.getToolUsage().get("calculator"));
        assertEquals(3, summary.getAgentUsage().get("general-assistant"));
        assertEquals(2, summary.getAgentUsage().get("sql-expert"));
    }

    @Test
    @DisplayName("时间区间过滤")
    void filterByRange() {
        LocalDateTime now = LocalDateTime.now();
        service.record(build("u1", "a1", true, false, 100, 1, List.of()));
        // 模拟一个 2 小时前的记录
        AgentMetricsRecord old = build("u1", "a1", true, false, 100, 1, List.of());
        old.setCreatedAt(now.minusHours(2));
        service.record(old);

        long count = service.countBetween(now.minusHours(1), now.plusMinutes(1));
        assertEquals(1, count);
    }

    @Test
    @DisplayName("最近 N 条按时间倒序")
    void recentSorted() {
        service.record(build("u1", "a", true, false, 100, 1, List.of()));
        sleep(5);
        service.record(build("u1", "b", true, false, 100, 1, List.of()));
        sleep(5);
        service.record(build("u1", "c", true, false, 100, 1, List.of()));

        List<AgentMetricsRecord> recent = service.recent(2);
        assertEquals(2, recent.size());
        assertEquals("c", recent.get(0).getAgentName());
        assertEquals("b", recent.get(1).getAgentName());
    }

    @Test
    @DisplayName("按用户查询")
    void recentByUser() {
        service.record(build("alice", "a", true, false, 100, 1, List.of()));
        service.record(build("bob", "a", true, false, 100, 1, List.of()));
        service.record(build("alice", "b", true, false, 100, 1, List.of()));

        List<AgentMetricsRecord> alice = service.recentByUser("alice", 10);
        assertEquals(2, alice.size());
        assertTrue(alice.stream().allMatch(r -> "alice".equals(r.getUserId())));
    }

    @Test
    @DisplayName("失败记录")
    void topFailures() {
        service.record(build("u", "a", true, false, 100, 1, List.of()));
        service.record(build("u", "a", false, false, 50, 0, List.of(), "boom"));
        service.record(build("u", "a", false, true, 5000, 10, List.of(), "timeout"));

        List<AgentMetricsRecord> failures = service.topFailures(5);
        assertEquals(2, failures.size());
        assertTrue(failures.stream().noneMatch(AgentMetricsRecord::isSuccess));
    }

    @Test
    @DisplayName("清空记录")
    void clear() {
        service.record(build("u", "a", true, false, 100, 1, List.of()));
        assertTrue(service.countAll() > 0);
        service.clear();
        assertEquals(0, service.countAll());
    }

    @Test
    @DisplayName("空数据汇总")
    void emptySummary() {
        AgentMetricsSummary s = service.summarize(null, LocalDateTime.now());
        assertEquals(0, s.getTotalRequests());
        assertEquals(0.0, s.getSuccessRate());
        assertEquals(0, s.getTotalTokens());
    }

    @Test
    @DisplayName("Top 用户列表")
    void topUsers() {
        for (int i = 0; i < 5; i++) service.record(build("alice", "a", true, false, 100, 1, List.of()));
        for (int i = 0; i < 2; i++) service.record(build("bob", "a", true, false, 100, 1, List.of()));
        for (int i = 0; i < 1; i++) service.record(build("carol", "a", true, false, 100, 1, List.of()));

        AgentMetricsSummary s = service.summarize(null, LocalDateTime.now().plusMinutes(1));
        assertEquals("alice", s.getTopUsers().get(0).getUserId());
        assertEquals(5, s.getTopUsers().get(0).getRequestCount());
    }

    private AgentMetricsRecord build(String userId, String agent, boolean success, boolean timeout,
                                       long duration, int iter, List<String> tools) {
        return build(userId, agent, success, timeout, duration, iter, tools, null);
    }

    private AgentMetricsRecord build(String userId, String agent, boolean success, boolean timeout,
                                       long duration, int iter, List<String> tools, String error) {
        return AgentMetricsRecord.builder()
            .userId(userId)
            .agentName(agent)
            .agentType("react")
            .input("test input")
            .success(success)
            .timeout(timeout)
            .error(error)
            .durationMs(duration)
            .iterations(iter)
            .inputTokens(100)
            .outputTokens(50)
            .totalTokens(150)
            .toolsUsed(String.join(",", tools))
            .modelName("MiniMax-M2")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
