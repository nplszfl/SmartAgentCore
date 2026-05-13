package com.agent.core.model;

import com.agent.core.memory.Memory;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * MiniMax模型适配器
 * 支持MiniMax Token Plan API
 */
@Slf4j
public class MiniMaxModelAdapter implements ChatModel {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;

    public MiniMaxModelAdapter(String apiKey) {
        this(apiKey, "abab6.5s-chat", "https://api.minimax.chat");
    }

    public MiniMaxModelAdapter(String apiKey, String model, String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public ModelResponse chat(List<Memory.Message> messages) {
        return chat(messages, Map.of());
    }

    @Override
    public ModelResponse chat(List<Memory.Message> messages, Map<String, Object> parameters) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", toMiniMaxMessages(messages));
            requestBody.put("stream", false);
            
            // Token Plan特有参数
            requestBody.put("tokens_to_generate", parameters.getOrDefault("max_tokens", 2048));
            requestBody.put("temperature", parameters.getOrDefault("temperature", 0.7));
            
            // 是否启用工具调用
            if (parameters.containsKey("tools")) {
                requestBody.put("tools", parameters.get("tools"));
            }

            String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/text/chatcompletion_v2"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.debug("MiniMax API响应: {}", response.body());
            
            if (response.statusCode() != 200) {
                log.error("MiniMax API错误: {} - {}", response.statusCode(), response.body());
                return ModelResponse.builder()
                    .content("API Error: " + response.statusCode() + " - " + response.body())
                    .done(true)
                    .build();
            }

            Map<String, Object> respMap = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(response.body(), Map.class);

            // 解析响应
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                String content = (String) message.get("content");
                
                // 解析工具调用
                ModelResponse.ToolCall[] toolCalls = parseToolCalls(message);
                
                Map<String, Integer> usage = parseUsage(respMap);
                
                return ModelResponse.builder()
                    .content(content)
                    .done(true)
                    .model(model)
                    .toolCalls(toolCalls)
                    .usage(usage)
                    .build();
            }

            return ModelResponse.builder().content("No response").done(true).build();

        } catch (Exception e) {
            log.error("调用MiniMax失败", e);
            return ModelResponse.builder()
                .content("Error: " + e.getMessage())
                .done(true)
                .build();
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    private List<Map<String, String>> toMiniMaxMessages(List<Memory.Message> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Memory.Message msg : messages) {
            Map<String, String> m = new HashMap<>();
            
            // MiniMax使用不同的role名称
            String role = switch (msg.role()) {
                case SYSTEM -> "system";
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case TOOL -> "user"; // MiniMax可能不支持tool角色
            };
            
            m.put("role", role);
            m.put("content", msg.content());
            if (msg.name() != null && msg.role() == Memory.Message.Role.TOOL) {
                m.put("name", msg.name());
            }
            result.add(m);
        }
        return result;
    }

    private ModelResponse.ToolCall[] parseToolCalls(Map<String, Object> message) {
        // MiniMax的工具调用解析逻辑
        if (message.containsKey("tool_calls")) {
            List<Map<String, Object>> toolCallsList = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCallsList != null) {
                return toolCallsList.stream()
                    .map(tc -> {
                        Map<String, Object> func = (Map<String, Object>) tc.get("function");
                        String args = func != null ? (String) func.get("arguments") : "{}";
                        Map<String, Object> argsMap = parseJson(args);
                        return ModelResponse.ToolCall.builder()
                            .id(tc.getOrDefault("id", UUID.randomUUID().toString()).toString())
                            .name(func != null ? (String) func.get("name") : "")
                            .arguments(argsMap)
                            .build();
                    })
                    .toArray(ModelResponse.ToolCall[]::new);
            }
        }
        return null;
    }

    private Map<String, Object> parseJson(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Integer> parseUsage(Map<String, Object> resp) {
        Map<String, Integer> usage = new HashMap<>();
        if (resp.containsKey("usage")) {
            Map<String, Object> usageMap = (Map<String, Object>) resp.get("usage");
            if (usageMap.containsKey("prompt_tokens")) {
                usage.put("prompt_tokens", ((Number) usageMap.get("prompt_tokens")).intValue());
            }
            if (usageMap.containsKey("completion_tokens")) {
                usage.put("completion_tokens", ((Number) usageMap.get("completion_tokens")).intValue());
            }
            if (usageMap.containsKey("total_tokens")) {
                usage.put("total_tokens", ((Number) usageMap.get("total_tokens")).intValue());
            }
        }
        return usage;
    }
}
