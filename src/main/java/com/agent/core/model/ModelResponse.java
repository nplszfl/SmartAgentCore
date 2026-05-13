package com.agent.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 模型响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelResponse {

    /**
     * 响应内容
     */
    private String content;

    /**
     * 是否完成
     */
    private boolean done;

    /**
     * 工具调用列表
     */
    private ToolCall[] toolCalls;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * token使用量
     */
    private Map<String, Integer> usage;

    /**
     * 工具调用
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private String id;
        private String name;
        private Map<String, Object> arguments;
    }
}
