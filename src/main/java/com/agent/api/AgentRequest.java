package com.agent.api;

import lombok.Data;
import java.util.Map;
import java.util.List;

/**
 * Agent执行请求
 */
@Data
public class AgentRequest {
    
    /**
     * Agent类型: react, plan, simple
     */
    private String agentType = "react";
    
    /**
     * 输入内容
     */
    private String input;
    
    /**
     * Agent名称
     */
    private String agentName;
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    /**
     * 工具列表
     */
    private List<String> tools;
    
    /**
     * 执行上下文
     */
    private Map<String, Object> context;
    
    /**
     * 最大迭代次数
     */
    private Integer maxIterations;

    /**
     * 图片Base64编码（用于OCR）
     */
    private String imageBase64;

    /**
     * 图片文件名
     */
    private String imageName;
}
