package com.agent.api;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
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
    @NotBlank(message = "input不能为空")
    @Size(max = 10000, message = "input长度不能超过10000")
    private String input;

    /**
     * Agent名称
     */
    @Size(max = 100, message = "agentName长度不能超过100")
    private String agentName;

    /**
     * 系统提示词
     */
    @Size(max = 5000, message = "systemPrompt长度不能超过5000")
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
    @Max(value = 50, message = "maxIterations不能超过50")
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
