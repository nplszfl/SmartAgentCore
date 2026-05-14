package com.agent.api;

import com.agent.core.memory.Memory;
import com.agent.core.model.ChatModel;
import com.agent.core.model.ModelResponse;
import com.agent.core.tool.*;
import com.agent.entity.ConversationEntity;
import com.agent.service.ConversationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent REST API Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    public AgentController(ChatModel chatModel, ConversationService conversationService) {
        this.chatModel = chatModel;
        this.conversationService = conversationService;
        this.toolRegistry = new ToolRegistry();
        // 注册默认工具
        toolRegistry.register(new SearchTool());
        toolRegistry.register(new CalculatorTool());
        toolRegistry.register(new DateTimeTool());
        toolRegistry.register(new OcrTool());
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Chat with Agent
     */
    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        try {
            String userId = getUserId(request);
            String input = request.getInput();
            int maxIterations = request.getMaxIterations() != null ? request.getMaxIterations() : 10;

            // 获取或创建会话
            ConversationEntity conversation = conversationService.getOrCreateConversation(
                userId,
                request.getAgentName() != null ? request.getAgentName() : "assistant",
                request.getSystemPrompt() != null ? request.getSystemPrompt() : buildDefaultSystemPrompt()
            );

            // 从数据库加载消息历史
            List<Memory.Message> messages = loadMessages(conversation);

            // 添加新用户消息
            if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
                List<Memory.ContentItem> contentItems = new ArrayList<>();
                if (input != null && !input.isEmpty()) {
                    contentItems.add(new Memory.ContentItem.Text(input));
                }
                String pureBase64 = extractPureBase64(request.getImageBase64());
                String format = extractImageFormat(request.getImageName());
                contentItems.add(new Memory.ContentItem.Image(pureBase64, format));
                messages.add(new Memory.Message(Memory.Message.Role.USER, contentItems));
            } else {
                messages.add(new Memory.Message(Memory.Message.Role.USER, input));
            }

            // 执行 ReAct 循环
            String finalAnswer = executeReActLoop(messages, maxIterations);

            // 更新数据库中的消息历史
            saveMessages(conversation.getId(), messages);

            return AgentResponse.success(finalAnswer);
        } catch (Exception e) {
            log.error("Chat处理失败", e);
            return AgentResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/conversations")
    public List<ConversationEntity> getConversations(@RequestParam String userId) {
        return conversationService.getUserConversations(userId);
    }

    /**
     * 获取单个会话详情
     */
    @GetMapping("/conversation/{id}")
    public AgentResponse getConversation(@PathVariable Long id) {
        // 这里可以添加获取详情的方法
        return AgentResponse.success("会话ID: " + id);
    }

    /**
     * 获取可用工具列表
     */
    @GetMapping("/tools")
    public List<Map<String, String>> getTools() {
        List<Map<String, String>> toolList = new ArrayList<>();
        for (Tool tool : toolRegistry.getAll()) {
            Map<String, String> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolList.add(toolInfo);
        }
        return toolList;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("model", chatModel.getModelName());
        status.put("tools", toolRegistry.getAll().size());
        return status;
    }

    // ============ 私有方法 ============

    private String getUserId(AgentRequest request) {
        if (request.getContext() != null && request.getContext().get("userId") != null) {
            return String.valueOf(request.getContext().get("userId"));
        }
        return "default";
    }

    private List<Memory.Message> loadMessages(ConversationEntity conversation) {
        List<Memory.Message> messages = new ArrayList<>();
        try {
            if (conversation.getMessages() != null && !conversation.getMessages().isEmpty()) {
                // 反序列化消息历史
                List<Map<String, Object>> msgList = objectMapper.readValue(
                    conversation.getMessages(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
                for (Map<String, Object> msgData : msgList) {
                    String roleStr = (String) msgData.get("role");
                    String content = (String) msgData.get("content");
                    Memory.Message.Role role = Memory.Message.Role.valueOf(roleStr.toUpperCase());
                    messages.add(new Memory.Message(role, content));
                }
            }
        } catch (Exception e) {
            log.warn("加载消息历史失败，使用空列表", e);
        }

        // 添加系统提示（如果还没有）
        if (messages.isEmpty() || messages.get(0).role() != Memory.Message.Role.SYSTEM) {
            String systemPrompt = conversation.getSystemPrompt();
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                systemPrompt = buildDefaultSystemPrompt();
            }
            messages.add(0, new Memory.Message(Memory.Message.Role.SYSTEM, systemPrompt));
        }

        return messages;
    }

    private void saveMessages(Long conversationId, List<Memory.Message> messages) {
        try {
            List<Map<String, String>> msgList = new ArrayList<>();
            for (Memory.Message msg : messages) {
                if (msg.role() != Memory.Message.Role.SYSTEM) {
                    Map<String, String> msgData = new HashMap<>();
                    msgData.put("role", msg.role().name().toLowerCase());
                    msgData.put("content", String.valueOf(msg.content()));
                    msgList.add(msgData);
                }
            }
            String messagesJson = objectMapper.writeValueAsString(msgList);
            conversationService.updateMessages(conversationId, messagesJson);
        } catch (Exception e) {
            log.error("保存消息历史失败", e);
        }
    }

    private String executeReActLoop(List<Memory.Message> messages, int maxIterations) {
        String finalAnswer = null;

        for (int step = 0; step < maxIterations; step++) {
            ModelResponse response = chatModel.chat(messages);
            String content = response.getContent();

            if (content == null || content.isEmpty()) {
                return "模型返回为空";
            }

            if (response.isDone()) {
                finalAnswer = extractFinalAnswer(content);
                break;
            }

            // 解析工具调用
            List<ModelResponse.ToolCall> toolCalls = parseToolCalls(content);
            if (toolCalls.isEmpty()) {
                finalAnswer = content;
                break;
            }

            // 执行工具调用
            for (ModelResponse.ToolCall tc : toolCalls) {
                Tool tool = toolRegistry.get(tc.getName());
                String toolResult;
                if (tool != null) {
                    Tool.ToolResult result = tool.execute(tc.getArguments());
                    toolResult = String.format("[TOOL: %s] %s",
                        tc.getName(),
                        result.success() ? result.output() : "Error: " + result.error());
                } else {
                    toolResult = "[TOOL: " + tc.getName() + "] Tool not found";
                }
                messages.add(new Memory.Message(Memory.Message.Role.ASSISTANT, content));
                messages.add(new Memory.Message(Memory.Message.Role.TOOL, toolResult, tc.getName()));
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "达到最大迭代次数限制";
        }

        return finalAnswer;
    }

    private String buildDefaultSystemPrompt() {
        return """
            你是一个智能助手，使用ReAct推理模式。

            推理规则：
            1. Thought: 思考需要做什么
            2. Action: 如果需要，调用工具（格式：Action: 工具名, Action Input: {"param": "value"}）
            3. Observation: 观察结果
            4. 重复直到得到最终答案
            5. Final Answer: 最终答案

            可用工具：
            - search: 搜索工具，用于查询信息
            - calculator: 计算器，用于数学计算
            - datetime: 日期时间工具，获取当前日期时间
            - ocr: OCR文字识别工具，从图片中提取文字

            当你确定知道答案时，直接返回：Final Answer: 你的答案
            """;
    }

    private List<ModelResponse.ToolCall> parseToolCalls(String content) {
        List<ModelResponse.ToolCall> calls = new ArrayList<>();

        if (content.contains("Action:") && content.contains("Action Input:")) {
            try {
                int actionIdx = content.indexOf("Action:");
                int inputIdx = content.indexOf("Action Input:");

                String actionPart = content.substring(actionIdx + 7, inputIdx).trim();
                String toolName = actionPart.split("\n")[0].trim();

                String inputPart = content.substring(inputIdx + 13).trim();
                int jsonStart = inputPart.indexOf("{");
                int jsonEnd = inputPart.lastIndexOf("}");

                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String json = inputPart.substring(jsonStart, jsonEnd + 1);
                    Map<String, Object> args = new HashMap<>();
                    json = json.substring(1, json.length() - 1);
                    for (String pair : json.split(",")) {
                        String[] kv = pair.split(":");
                        if (kv.length == 2) {
                            String key = kv[0].trim().replace("\"", "");
                            String value = kv[1].trim().replace("\"", "");
                            args.put(key, value);
                        }
                    }
                    calls.add(new ModelResponse.ToolCall(UUID.randomUUID().toString(), toolName, args));
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        return calls;
    }

    private String extractFinalAnswer(String content) {
        if (content.contains("Final Answer:")) {
            int idx = content.indexOf("Final Answer:");
            return content.substring(idx + 13).trim();
        }
        return content;
    }

    private String extractPureBase64(String base64Data) {
        if (base64Data.contains(",")) {
            return base64Data.split(",")[1];
        }
        return base64Data;
    }

    private String extractImageFormat(String fileName) {
        if (fileName == null) return "jpeg";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".webp")) return "webp";
        if (lower.endsWith(".gif")) return "gif";
        return "jpeg";
    }
}
