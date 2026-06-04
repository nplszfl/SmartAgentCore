package com.agent.service;

import com.agent.dto.AgentMetricsSummary;
import com.agent.entity.AgentMetricsRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 调用指标服务
 *
 * 用于记录和汇总 Agent 每次调用的执行情况：成功率、耗时分布、token 用量、工具使用频次等。
 * 该服务为 Agent 平台提供可观测性能力。
 */
public interface AgentMetricsService {

    /**
     * 记录一次 Agent 调用
     */
    void record(AgentMetricsRecord record);

    /**
     * 获取汇总（按时间区间）
     */
    AgentMetricsSummary summarize(LocalDateTime from, LocalDateTime to);

    /**
     * 获取最近的执行记录
     */
    List<AgentMetricsRecord> recent(int limit);

    /**
     * 获取指定用户最近的执行记录
     */
    List<AgentMetricsRecord> recentByUser(String userId, int limit);

    /**
     * 获取失败率最高的 Agent
     */
    List<AgentMetricsRecord> topFailures(int limit);

    /**
     * 获取总调用次数
     */
    long countAll();

    /**
     * 获取时间区间内的调用次数
     */
    long countBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 清空所有记录（测试/管理用）
     */
    void clear();
}
