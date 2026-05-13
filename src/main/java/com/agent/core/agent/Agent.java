package com.agent.core.agent;

import com.agent.core.memory.Memory;
import com.agent.core.tool.Tool;
import com.agent.core.model.ModelResponse;

import java.util.List;
import java.util.Map;

/**
 * Agent核心接口
 * Agent is the core entity that can perceive, think, and act
 */
public interface Agent {

    /**
     * 获取Agent名称
     */
    String getName();

    /**
     * 获取Agent描述
     */
    String getDescription();

    /**
     * 执行Agent任务
     * @param input 用户输入
     * @param context 执行上下文
     * @return Agent响应
     */
    ModelResponse execute(String input, Map<String, Object> context);

    /**
     * 执行Agent任务（带记忆）
     * @param input 用户输入
     * @param memory 记忆组件
     * @param context 执行上下文
     * @return Agent响应
     */
    ModelResponse execute(String input, Memory memory, Map<String, Object> context);

    /**
     * 获取Agent可用的工具列表
     */
    List<Tool> getTools();

    /**
     * 添加工具到Agent
     */
    void addTool(Tool tool);

    /**
     * 移除工具
     */
    void removeTool(String toolName);

    /**
     * 获取系统提示词
     */
    String getSystemPrompt();
}
