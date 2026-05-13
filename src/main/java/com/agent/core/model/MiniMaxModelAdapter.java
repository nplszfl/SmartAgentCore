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
 * 支持MiniMax M2.7多模态（通过text端点发送图片base64）
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
            // MiniMax-M2.7 使用 /v1/text/chatcompletion_v2 端点（统一端点支持多模态）
            String endpoint = "/v1/text/chatcompletion_v2";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", toMiniMaxMessages(messages));
            requestBody.put("stream", false);
            requestBody.put("max_tokens", parameters.getOrDefault("max_tokens", 4096));

            String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(requestBody);

            log.info("[MiniMax] 请求 endpoint={}, model={}", endpoint, model);
            log.debug("[MiniMax] 请求体: {}", jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(120))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.info("[MiniMax] 响应状态: {}, body长度: {}", response.statusCode(), response.body().length());
            log.debug("[MiniMax] API响应: {}", response.body());
            
            if (response.statusCode() != 200) {
                log.error("MiniMax API错误: {} - {}", response.statusCode(), response.body());
                return ModelResponse.builder()
                    .content("API Error: " + response.statusCode() + " - " + response.body())
                    .done(true)
                    .build();
            }

            Map<String, Object> respMap = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(response.body(), Map.class);

            // 检查base_resp错误
            if (respMap.containsKey("base_resp")) {
                Map<String, Object> baseResp = (Map<String, Object>) respMap.get("base_resp");
                int statusCode = ((Number) baseResp.get("status_code")).intValue();
                if (statusCode != 0) {
                    String statusMsg = (String) baseResp.get("status_msg");
                    log.error("MiniMax API错误: status_code={}, msg={}", statusCode, statusMsg);
                    return ModelResponse.builder()
                        .content("API Error: " + statusCode + " - " + statusMsg)
                        .done(true)
                        .build();
                }
            }

            // 解析 choices 格式（MiniMax-M2.7使用choices返回）
            String content = null;
            ModelResponse.ToolCall[] toolCalls = null;
            Map<String, Integer> usage = parseUsage(respMap);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null) {
                    // 优先取 reasoning_content（M2.7的思考过程），其次 content
                    content = (String) message.get("reasoning_content");
                    if (content == null || content.isEmpty()) {
                        content = (String) message.get("content");
                    }
                    // 工具调用
                    if (message.containsKey("tool_calls")) {
                        toolCalls = parseToolCalls(message);
                    }
                }
            }

            if (content == null || content.isEmpty()) {
                log.warn("[MiniMax] 响应content为空，可能max_tokens不足或模型未返回内容");
                content = "（图片识别完成，描述内容为空）";
            }

            return ModelResponse.builder()
                .content(content)
                .done(true)
                .model(model)
                .toolCalls(toolCalls)
                .usage(usage)
                .build();

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

    private List<Object> toMiniMaxMessages(List<Memory.Message> messages) {
        List<Object> result = new ArrayList<>();
        for (Memory.Message msg : messages) {
            if (msg.hasImage()) {
                // 多模态消息 - MiniMax格式：描述\ndata:image/xxx;base64,xxx
                StringBuilder content = new StringBuilder();
                for (Memory.ContentItem item : msg.getContentItems()) {
                    if (item instanceof Memory.ContentItem.Text t) {
                        content.append(t.text()).append("\n");
                    } else if (item instanceof Memory.ContentItem.Image img) {
                        // MiniMax多模态：文本描述 + base64
                        if (content.length() == 0) {
                            content.append("请描述这张图片。\n");
                        }
                        content.append("data:image/").append(img.format()).append(";base64,").append(img.base64());
                    }
                }
                Map<String, Object> m = new HashMap<>();
                m.put("role", toMiniMaxRole(msg.role()));
                m.put("content", content.toString().trim());
                result.add(m);
            } else {
                // 纯文本消息
                Map<String, Object> m = new HashMap<>();
                m.put("role", toMiniMaxRole(msg.role()));
                m.put("content", msg.content());
                result.add(m);
            }
        }
        return result;
    }

    private String toMiniMaxRole(Memory.Message.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "user";
        };
    }

    private ModelResponse.ToolCall[] parseToolCalls(Map<String, Object> message) {
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
