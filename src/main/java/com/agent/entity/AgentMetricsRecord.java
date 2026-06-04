package com.agent.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 调用指标记录
 *
 * 记录每一次 chat 请求的执行情况：耗时、token、是否成功、是否超时、使用的工具。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetricsRecord {

    /** 唯一 ID */
    private Long id;

    /** 用户 ID */
    private String userId;

    /** Agent 名称 */
    private String agentName;

    /** Agent 类型 (react/plan/simple) */
    private String agentType;

    /** 输入内容（可能截断） */
    private String input;

    /** 是否成功 */
    private boolean success;

    /** 是否超时 */
    private boolean timeout;

    /** 错误信息 */
    private String error;

    /** 耗时（毫秒） */
    private long durationMs;

    /** 迭代次数 */
    private int iterations;

    /** 输入 token */
    private int inputTokens;

    /** 输出 token */
    private int outputTokens;

    /** 总 token */
    private int totalTokens;

    /** 使用的工具（逗号分隔） */
    private String toolsUsed;

    /** 模型名称 */
    private String modelName;

    /** 时间戳 */
    private LocalDateTime createdAt;
}
