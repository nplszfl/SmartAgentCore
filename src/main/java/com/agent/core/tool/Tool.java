package com.agent.core.tool;

import java.util.Map;

/**
 * 工具接口
 * Tools enable Agents to interact with external systems
 */
public interface Tool {

    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 获取工具参数描述（JSON Schema格式）
     */
    String getParameterSchema();

    /**
     * 执行工具
     * @param parameters 工具参数
     * @return 工具执行结果
     */
    ToolResult execute(Map<String, Object> parameters);

    /**
     * 工具执行结果
     */
    record ToolResult(boolean success, String output, String error) {

        public static ToolResult success(String output) {
            return new ToolResult(true, output, null);
        }

        public static ToolResult failure(String error) {
            return new ToolResult(false, null, error);
        }
    }
}
