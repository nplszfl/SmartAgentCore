package com.agent.core.executor;

import com.agent.core.agent.Agent;
import com.agent.core.memory.Memory;
import com.agent.core.memory.ConversationMemory;
import com.agent.core.model.ChatModel;
import com.agent.core.model.ModelResponse;
import com.agent.core.tool.Tool;
import com.agent.core.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Agent执行器 - 负责驱动Agent执行任务
 */
@Slf4j
public class AgentExecutor {

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    private final long timeoutMs;

    public AgentExecutor(ChatModel chatModel) {
        this(chatModel, new ToolRegistry(), 10, 60000);
    }

    public AgentExecutor(ChatModel chatModel, ToolRegistry toolRegistry, int maxIterations, long timeoutMs) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.maxIterations = maxIterations;
        this.timeoutMs = timeoutMs;
    }

    /**
     * 执行Agent
     */
    public AgentResponse execute(Agent agent, String input, Map<String, Object> context) {
        return execute(agent, new ConversationMemory(), input, context);
    }

    /**
     * 执行Agent（带记忆）
     */
    public AgentResponse execute(Agent agent, Memory memory, String input, Map<String, Object> context) {
        // 初始化上下文
        if (context == null) {
            context = new HashMap<>();
        }

        // 添加用户消息到记忆
        memory.addUserMessage(input);

        // 构建系统提示词
        String systemPrompt = buildSystemPrompt(agent);

        // 构建完整消息列表
        List<Memory.Message> allMessages = new ArrayList<>();
        allMessages.add(new Memory.Message(Memory.Message.Role.SYSTEM, systemPrompt));
        allMessages.addAll(memory.getMessages());

        // 执行循环
        for (int i = 0; i < maxIterations; i++) {
            log.info("Agent执行 iteration {}", i + 1);

            // 调用模型
            ModelResponse response = chatModel.chat(allMessages);

            // 检查是否需要调用工具
            if (response.getToolCalls() != null && response.getToolCalls().length > 0) {
                // 处理工具调用
                for (ModelResponse.ToolCall toolCall : response.getToolCalls()) {
                    Tool tool = toolRegistry.get(toolCall.getName());
                    if (tool != null) {
                        log.info("执行工具: {}", toolCall.getName());
                        Tool.ToolResult result = tool.execute(toolCall.getArguments());
                        String resultContent = result.success() ? result.output() : "Error: " + result.error();
                        
                        // 添加助手消息和工具结果
                        allMessages.add(new Memory.Message(Memory.Message.Role.ASSISTANT, response.getContent()));
                        allMessages.add(new Memory.Message(Memory.Message.Role.TOOL, resultContent, toolCall.getName()));
                        memory.addAssistantMessage(response.getContent());
                        memory.addMessage(new Memory.Message(Memory.Message.Role.TOOL, resultContent, toolCall.getName()));
                    } else {
                        log.warn("工具不存在: {}", toolCall.getName());
                        allMessages.add(new Memory.Message(Memory.Message.Role.ASSISTANT, 
                            "Tool not found: " + toolCall.getName()));
                    }
                }
            } else {
                // 最终响应
                memory.addAssistantMessage(response.getContent());
                return new AgentResponse(response.getContent(), false, i + 1, response.getUsage());
            }
        }

        return new AgentResponse("执行超时，未能在指定迭代次数内完成任务", true, maxIterations, null);
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(Agent agent) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("# 角色\n");
        prompt.append(agent.getSystemPrompt()).append("\n\n");
        
        // 添加工具描述
        List<Tool> tools = agent.getTools();
        if (!tools.isEmpty()) {
            prompt.append("# 可用工具\n");
            for (Tool tool : tools) {
                prompt.append("## ").append(tool.getName()).append("\n");
                prompt.append(tool.getDescription()).append("\n");
                prompt.append("参数: ").append(tool.getParameterSchema()).append("\n\n");
            }
        }
        
        prompt.append("# 指令\n");
        prompt.append("当需要完成复杂任务时，你应该主动使用工具来解决问题。\n");
        prompt.append("每次只能调用一个工具。\n");
        prompt.append("如果工具执行失败，请尝试其他方法或返回错误。\n");
        
        return prompt.toString();
    }

    /**
     * Agent执行响应
     */
    public record AgentResponse(
        String content,
        boolean timeout,
        int iterations,
        Map<String, Integer> usage
    ) {}
}
