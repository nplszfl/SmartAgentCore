package com.agent.api;

import com.agent.config.AgentConfig;
import com.agent.core.memory.ConversationMemory;
import com.agent.core.memory.Memory;
import com.agent.core.model.ChatModel;
import com.agent.core.model.ModelResponse;
import com.agent.core.tool.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent REST API Controller
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    
    private final ChatModel chatModel;
    private final Map<String, Memory> userMemories = new HashMap<>();
    private final ToolRegistry toolRegistry;
    
    public AgentController(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.toolRegistry = new ToolRegistry();
        // 注册默认工具
        toolRegistry.register(new SearchTool());
        toolRegistry.register(new CalculatorTool());
        toolRegistry.register(new DateTimeTool());
        toolRegistry.register(new OcrTool());
    }
    
    /**
     * Chat with Agent
     */
    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        try {
            String userId = "default";
            if (request.getContext() != null && request.getContext().get("userId") != null) {
                userId = String.valueOf(request.getContext().get("userId"));
            }
            
            // Get or create memory for user
            Memory memory = userMemories.computeIfAbsent(userId, k -> new ConversationMemory());
            
            // Create simple reactive agent (inline implementation)
            String input = request.getInput();
            int maxIterations = request.getMaxIterations() != null ? request.getMaxIterations() : 10;
            
            // Build messages with system prompt
            List<Memory.Message> messages = new ArrayList<>();
            String systemPrompt = buildSystemPrompt();
            messages.add(new Memory.Message(Memory.Message.Role.SYSTEM, systemPrompt));
            
            // Add conversation history (skip system messages)
            for (Memory.Message msg : memory.getMessages()) {
                if (msg.role() != Memory.Message.Role.SYSTEM) {
                    messages.add(msg);
                }
            }
            messages.add(new Memory.Message(Memory.Message.Role.USER, input));
            
            // Execute ReAct loop
            String finalAnswer = null;
            for (int step = 0; step < maxIterations; step++) {
                // Call model
                ModelResponse response = chatModel.chat(messages);
                String content = response.getContent();
                
                if (content == null || content.isEmpty()) {
                    return AgentResponse.error("模型返回为空");
                }
                
                // Check if done
                if (response.isDone()) {
                    finalAnswer = extractFinalAnswer(content);
                    break;
                }
                
                // Parse and execute tool calls
                List<ModelResponse.ToolCall> toolCalls = parseToolCalls(content);
                if (toolCalls.isEmpty()) {
                    // No tool call, this is the final answer
                    finalAnswer = content;
                    break;
                }
                
                // Execute tool calls
                for (ModelResponse.ToolCall tc : toolCalls) {
                    Tool tool = toolRegistry.get(tc.getName());
                    if (tool != null) {
                        Tool.ToolResult result = tool.execute(tc.getArguments());
                        String toolResult = String.format(
                            "[TOOL: %s] %s", 
                            tc.getName(), 
                            result.success() ? result.output() : "Error: " + result.error()
                        );
                        messages.add(new Memory.Message(Memory.Message.Role.ASSISTANT, content));
                        messages.add(new Memory.Message(Memory.Message.Role.TOOL, toolResult, tc.getName()));
                    } else {
                        messages.add(new Memory.Message(Memory.Message.Role.ASSISTANT, content));
                        messages.add(new Memory.Message(Memory.Message.Role.TOOL, 
                            "[TOOL: " + tc.getName() + "] Tool not found", tc.getName()));
                    }
                }
            }
            
            if (finalAnswer == null) {
                finalAnswer = "达到最大迭代次数限制";
            }
            
            // Add to memory
            memory.addUserMessage(input);
            memory.addAssistantMessage(finalAnswer);
            
            return AgentResponse.success(finalAnswer);
        } catch (Exception e) {
            return AgentResponse.error(e.getMessage());
        }
    }
    
    private String buildSystemPrompt() {
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
            
            当你确定知道答案时，直接返回：Final Answer: 你的答案
            """;
    }
    
    private List<ModelResponse.ToolCall> parseToolCalls(String content) {
        List<ModelResponse.ToolCall> calls = new ArrayList<>();
        
        // Simple parsing for Action: toolname and Action Input: {...}
        if (content.contains("Action:") && content.contains("Action Input:")) {
            try {
                int actionIdx = content.indexOf("Action:");
                int inputIdx = content.indexOf("Action Input:");
                
                String actionPart = content.substring(actionIdx + 7, inputIdx).trim();
                String toolName = actionPart.split("\\n")[0].trim();
                
                String inputPart = content.substring(inputIdx + 13).trim();
                // Find JSON object
                int jsonStart = inputPart.indexOf("{");
                int jsonEnd = inputPart.lastIndexOf("}");
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String json = inputPart.substring(jsonStart, jsonEnd + 1);
                    Map<String, Object> args = new HashMap<>();
                    // Simple JSON parsing
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
                // Ignore parsing errors
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
    
    /**
     * Get available tools
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
     * Clear user memory
     */
    @DeleteMapping("/memory/{userId}")
    public AgentResponse clearMemory(@PathVariable String userId) {
        userMemories.remove(userId);
        return AgentResponse.success("Memory cleared for user: " + userId);
    }
    
    /**
     * Health check
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("model", "MiniMax");
        status.put("tools", toolRegistry.getAll().size());
        return status;
    }
}
