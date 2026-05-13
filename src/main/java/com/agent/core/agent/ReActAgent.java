package com.agent.core.agent;

import com.agent.core.memory.Memory;
import com.agent.core.memory.ConversationMemory;
import com.agent.core.model.ModelResponse;
import com.agent.core.tool.Tool;
import com.agent.core.tool.ToolRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReAct Agent实现
 * ReAct = Reasoning + Acting
 * 基于ReAct模式的智能Agent，能够进行多轮推理和工具调用
 */
@Slf4j
public class ReActAgent implements Agent {

    private final String name;
    private final String description;
    private final String systemPrompt;
    private final ToolRegistry toolRegistry;
    private final int maxSteps;

    @Builder
    public ReActAgent(
            String name,
            String description,
            String systemPrompt,
            ToolRegistry toolRegistry,
            int maxSteps
    ) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        this.maxSteps = maxSteps > 0 ? maxSteps : 10;
    }

    public static ReActAgentBuilder builder() {
        return new ReActAgentBuilder();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ModelResponse execute(String input, Map<String, Object> context) {
        return execute(input, new ConversationMemory(), context);
    }

    @Override
    public ModelResponse execute(String input, Memory memory, Map<String, Object> context) {
        // 保存原始输入
        String originalInput = input;
        
        // 构建ReAct提示词
        String reactPrompt = buildReActPrompt(input);
        
        // 构建消息列表
        List<Memory.Message> messages = new ArrayList<>();
        messages.add(new Memory.Message(Memory.Message.Role.SYSTEM, reactPrompt));
        
        // 添加历史记忆
        for (Memory.Message msg : memory.getMessages()) {
            if (msg.role() != Memory.Message.Role.SYSTEM) {
                messages.add(msg);
            }
        }

        // 执行多步推理
        for (int step = 0; step < maxSteps; step++) {
            log.debug("ReAct Agent step {}", step + 1);
            
            // 调用模型
            ModelResponse response = callModel(messages);
            
            if (response.isDone()) {
                return response;
            }

            // 处理工具调用
            if (response.getToolCalls() != null && response.getToolCalls().length > 0) {
                for (ModelResponse.ToolCall toolCall : response.getToolCalls()) {
                    Tool tool = toolRegistry.get(toolCall.getName());
                    if (tool != null) {
                        Tool.ToolResult result = tool.execute(toolCall.getArguments());
                        String resultJson = String.format(
                            "{\"tool\": \"%s\", \"success\": %s, \"output\": \"%s\"}",
                            toolCall.getName(),
                            result.success(),
                            result.success() ? result.output().replace("\"", "\\\"") : result.error().replace("\"", "\\\"")
                        );
                        messages.add(new Memory.Message(Memory.Message.Role.TOOL, resultJson, toolCall.getName()));
                    }
                }
            } else {
                // 完成
                return response;
            }
        }

        return ModelResponse.builder()
            .content("达到最大步数限制")
            .done(true)
            .build();
    }

    /**
     * 调用模型
     */
    protected ModelResponse callModel(List<Memory.Message> messages) {
        // 默认实现，实际由子类或注入的ChatModel提供
        throw new UnsupportedOperationException("需要注入ChatModel");
    }

    /**
     * 构建ReAct提示词
     */
    private String buildReActPrompt(String input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(systemPrompt).append("\n\n");
        prompt.append("# ReAct推理框架\n\n");
        prompt.append("你是一个智能助手，能够通过多步推理来解决问题。\n\n");
        prompt.append("## 推理模式\n");
        prompt.append("对于每个问题，你应该：\n");
        prompt.append("1. Thought: 思考需要做什么\n");
        prompt.append("2. Action: 如果需要，调用工具\n");
        prompt.append("3. Observation: 观察工具返回结果\n");
        prompt.append("4. ... 重复直到得到最终答案\n\n");
        prompt.append("## 输出格式\n");
        prompt.append("当调用工具时，返回：\n");
        prompt.append("Thought: <你的思考>\n");
        prompt.append("Action: <工具名称>\n");
        prompt.append("Action Input: <工具参数JSON>\n\n");
        prompt.append("最终完成时，返回：\n");
        prompt.append("Thought: <最终思考>\n");
        prompt.append("Final Answer: <最终答案>\n\n");
        
        // 添加工具
        if (!toolRegistry.getAll().isEmpty()) {
            prompt.append("# 可用工具\n");
            for (Tool tool : toolRegistry.getAll()) {
                prompt.append("- ").append(tool.getName()).append(": ")
                      .append(tool.getDescription()).append("\n");
            }
        }
        
        prompt.append("\n# 问题\n").append(input);
        
        return prompt.toString();
    }

    @Override
    public List<Tool> getTools() {
        return toolRegistry.getAll();
    }

    @Override
    public void addTool(Tool tool) {
        toolRegistry.register(tool);
    }

    @Override
    public void removeTool(String toolName) {
        toolRegistry.remove(toolName);
    }

    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
