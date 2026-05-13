package com.agent.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Agent执行响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    
    /**
     * 执行状态
     */
    private boolean success;
    
    /**
     * 响应内容
     */
    private String content;
    
    /**
     * 是否超时
     */
    private boolean timeout;
    
    /**
     * 执行迭代次数
     */
    private int iterations;
    
    /**
     * Token使用量
     */
    private Map<String, Integer> usage;
    
    /**
     * 错误信息
     */
    private String error;
    
    public static AgentResponse success(String content) {
        return AgentResponse.builder()
            .success(true)
            .content(content)
            .timeout(false)
            .build();
    }
    
    public static AgentResponse error(String error) {
        return AgentResponse.builder()
            .success(false)
            .error(error)
            .build();
    }
}
