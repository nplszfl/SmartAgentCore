package com.agent.core.agent;

import com.agent.core.memory.Memory;
import com.agent.core.memory.ConversationMemory;
import com.agent.core.model.ChatModel;
import com.agent.core.model.ModelResponse;
import com.agent.core.tool.Tool;
import com.agent.core.tool.ToolRegistry;
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

    /** Optional injected chat model. When present, {@link #callModel} delegates here. */
    private final ChatModel chatModel;

    /** Default number of recent messages added from memory at the start of each loop. */
    static final int DEFAULT_HISTORY_LIMIT = 20;

    public ReActAgent(
            String name,
            String description,
            String systemPrompt,
            ToolRegistry toolRegistry,
            int maxSteps
    ) {
        this(name, description, systemPrompt, toolRegistry, maxSteps, null);
    }

    /**
     * Full constructor. {@code chatModel} may be null — in that case {@link #callModel}
     * falls back to throwing {@link UnsupportedOperationException}, preserving the
     * legacy behaviour for tests that subclass and override callModel directly.
     */
    public ReActAgent(
            String name,
            String description,
            String systemPrompt,
            ToolRegistry toolRegistry,
            int maxSteps,
            ChatModel chatModel
    ) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        this.maxSteps = maxSteps > 0 ? maxSteps : 10;
        this.chatModel = chatModel;
    }

    /**
     * Builder for backward compatibility. Does not expose ChatModel — the legacy
     * {@link #callModel} override path is preserved for subclasses.
     */
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
        // 1. Input validation
        ModelResponse invalid = validateInput(input);
        if (invalid != null) {
            return invalid;
        }

        // Save the original (validated, non-null) input
        String originalInput = input.trim();

        // 2. Build ReAct prompt
        String reactPrompt = buildReActPrompt(originalInput);

        // 3. Build message list: system + recent history
        List<Memory.Message> messages = new ArrayList<>();
        messages.add(new Memory.Message(Memory.Message.Role.SYSTEM, reactPrompt));

        int historyLimit = DEFAULT_HISTORY_LIMIT;
        List<Memory.Message> recentMsgs = memory.getRecentMessages(historyLimit);
        for (Memory.Message msg : recentMsgs) {
            if (msg.role() != Memory.Message.Role.SYSTEM) {
                messages.add(msg);
            }
        }

        // 4. Multi-step reasoning loop
        for (int step = 0; step < maxSteps; step++) {
            log.debug("ReAct Agent step {} of {}", step + 1, maxSteps);

            ModelResponse response = callModel(messages);

            if (response == null) {
                return ModelResponse.builder()
                        .content("模型返回为空")
                        .done(true)
                        .build();
            }
            if (response.isDone()) {
                return response;
            }

            // 5. Tool dispatch
            ModelResponse.ToolCall[] toolCalls = response.getToolCalls();
            if (toolCalls == null || toolCalls.length == 0) {
                // No tool calls and not done: treat as final answer
                return response;
            }

            for (ModelResponse.ToolCall toolCall : toolCalls) {
                Tool tool = toolRegistry.get(toolCall.getName());
                Tool.ToolResult result;
                if (tool == null) {
                    result = Tool.ToolResult.failure("工具不存在: " + toolCall.getName());
                } else {
                    try {
                        result = tool.execute(toolCall.getArguments());
                    } catch (Exception e) {
                        log.error("Tool '{}' threw: {}", toolCall.getName(), e.toString());
                        result = Tool.ToolResult.failure(
                                toolCall.getName() + " 执行异常: " + e.getMessage());
                    }
                }
                messages.add(new Memory.Message(
                        Memory.Message.Role.TOOL,
                        formatToolResult(toolCall.getName(), result),
                        toolCall.getName()));
            }
        }

        // 6. Max steps reached
        return ModelResponse.builder()
                .content(String.format(
                        "已达到最大步数限制 (%d 步)，未能得出最终答案。请尝试简化问题或提供更多上下文。",
                        maxSteps))
                .done(true)
                .build();
    }

    /** Null/blank input is rejected with a clear error before any model call. */
    private ModelResponse validateInput(String input) {
        if (input == null) {
            return ModelResponse.builder()
                    .content("输入不能为空 (input is null)")
                    .done(true)
                    .build();
        }
        if (input.trim().isEmpty()) {
            return ModelResponse.builder()
                    .content("输入不能为空白 (input is blank)")
                    .done(true)
                    .build();
        }
        return null;
    }

    /**
     * Format a tool result into a stable JSON envelope that survives arbitrary
     * user output (newlines, quotes, control chars). The previous hand-rolled
     * string-replace approach was unsafe — replacing only {@code "} with {@code \"}
     * leaves CR/LF in the value, which the downstream model will mis-parse.
     */
    String formatToolResult(String toolName, Tool.ToolResult result) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\\\"tool\\\":\\\"").append(escapeJson(toolName))
          .append("\\\",\\\"success\\\":").append(result.success())
          .append(",\\\"output\\\":\\\"")
          .append(escapeJson(result.success() ? result.output() : result.error()))
          .append("\\\"}");
        return sb.toString();
    }

    /** Minimal JSON string escape: handles ", \, control chars. */
    static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    /**
     * 调用模型 — delegates to the injected ChatModel when present, otherwise
     * throws to preserve the legacy override path for subclasses.
     */
    protected ModelResponse callModel(List<Memory.Message> messages) {
        if (chatModel != null) {
            return chatModel.chat(messages);
        }
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

    /**
     * Builder for the legacy 5-arg constructor (preserves callers that used to
     * subclass and override callModel).
     */
    public static class ReActAgentBuilder {
        private String name = "agent";
        private String description = "";
        private String systemPrompt = "";
        private ToolRegistry toolRegistry = new ToolRegistry();
        private int maxSteps = 10;

        public ReActAgentBuilder name(String name) { this.name = name; return this; }
        public ReActAgentBuilder description(String d) { this.description = d; return this; }
        public ReActAgentBuilder systemPrompt(String s) { this.systemPrompt = s; return this; }
        public ReActAgentBuilder toolRegistry(ToolRegistry r) { this.toolRegistry = r; return this; }
        public ReActAgentBuilder maxSteps(int n) { this.maxSteps = n; return this; }
        public ReActAgent build() {
            return new ReActAgent(name, description, systemPrompt, toolRegistry, maxSteps);
        }
    }
}
