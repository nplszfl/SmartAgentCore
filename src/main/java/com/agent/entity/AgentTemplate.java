package com.agent.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 模板 - 预配置好的常用 Agent 角色
 *
 * 业务价值：让用户不用从零开始配置 Agent，可以直接复用模板。
 * 例如：通用助手、SQL 专家、代码审查员、翻译官、文案写手等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTemplate {

    /** 模板唯一标识 (slug) */
    private String id;

    /** 模板名称 */
    private String name;

    /** 分类 (general/code/writing/analysis/support) */
    private String category;

    /** 模板描述 */
    private String description;

    /** 模板图标 (emoji) */
    private String icon;

    /** 默认系统提示词 */
    private String systemPrompt;

    /** 默认工具列表 */
    private List<String> tools;

    /** 默认最大迭代次数 */
    private int maxIterations;

    /** 推荐的模型 */
    private String recommendedModel;

    /** 推荐参数（如 temperature、maxTokens） */
    private Map<String, Object> recommendedParams;

    /** 使用次数（运行时会更新） */
    private long usageCount;
}
