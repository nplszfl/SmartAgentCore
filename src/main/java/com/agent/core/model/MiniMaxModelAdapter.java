package com.agent.core.model;

import com.agent.core.memory.Memory;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

/**
 * MiniMax模型适配器
 * 
 * MiniMax-M2.7 原生支持多模态输入，图片直接通过 base64 格式发送
 * 无需先调用 VL 模型提取描述
 */
@Slf4j
public class MiniMaxModelAdapter implements ChatModel {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient httpClient;
    
    // VL模型专用端点（参考OpenClaw）
    // 实际用 https://api.minimax.io/anthropic 作为base
    private static final String VL_BASE_URL = "https://api.minimax.io/anthropic";

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
            // MiniMax-M2.7 原生支持多模态，直接发送图片
            String endpoint = "/v1/text/chatcompletion_v2";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", toMiniMaxMessagesWithImages(messages));
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
                content = "（响应为空）";
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

    /**
     * 处理消息中的图片：用 VL-01 提取描述，替换图片为文字描述
     */
    private List<Memory.Message> processImagesInMessages(List<Memory.Message> messages) {
        List<Memory.Message> result = new ArrayList<>();
        for (Memory.Message msg : messages) {
            if (msg.hasImage()) {
                // content是List<ContentItem>的情况
                StringBuilder textContent = new StringBuilder();
                for (Memory.ContentItem item : msg.getContentItems()) {
                    if (item instanceof Memory.ContentItem.Text t) {
                        if (textContent.length() > 0) textContent.append("\n");
                        textContent.append(t.text());
                    } else if (item instanceof Memory.ContentItem.Image img) {
                        // 用 VL-01 提取图片描述
                        String description = extractImageDescription(img.base64(), img.format());
                        if (textContent.length() > 0) textContent.append("\n");
                        textContent.append("[图片描述: ").append(description).append("]");
                    }
                }
                result.add(new Memory.Message(msg.role(), textContent.toString()));
            } else {
                // 检查content字符串是否包含"[Image["文本，如果有就处理
                Object rawContent = msg.content();
                if (rawContent instanceof String content && content.contains("[Image[")) {
                    // 替换[Image[base64=...]]为图片描述
                    String processed = processImageText(content);
                    result.add(new Memory.Message(msg.role(), processed));
                } else {
                    result.add(msg);
                }
            }
        }
        return result;
    }

    /**
     * 处理字符串中包含[Image[base64=...]]格式的图片文本
     * 提取base64并用VL-01生成描述
     */
    private String processImageText(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int imgStart = text.indexOf("[Image[", i);
            if (imgStart == -1) {
                result.append(text.substring(i));
                break;
            }
            // 追加[Image[之前的文本
            result.append(text, i, imgStart);
            
            // 找到base64=之后的内容
            int base64Start = text.indexOf("base64=", imgStart);
            if (base64Start == -1 || base64Start > imgStart + 100) {
                // 格式不对，跳过这个标记
                result.append("[Image[");
                i = imgStart + 7;
                continue;
            }
            
            base64Start += 7; // 跳过"base64="
            int imgEnd = text.indexOf("]]", base64Start);
            if (imgEnd == -1) {
                // 没有结束标记，尝试其他方式
                result.append("[Image[");
                i = imgStart + 7;
                continue;
            }
            
            String base64 = text.substring(base64Start, imgEnd);
            // 找到format在base64=之前
            int formatStart = text.lastIndexOf("[Image[format=", imgStart);
            String format = "jpeg";
            if (formatStart != -1 && formatStart > imgStart - 30) {
                int fmtEnd = text.indexOf("]", formatStart);
                if (fmtEnd > formatStart) {
                    format = text.substring(formatStart + 14, fmtEnd);
                }
            }
            
            // 用VL-01提取描述
            String description = extractImageDescription(base64, format);
            result.append("[图片描述: ").append(description).append("]");
            
            i = imgEnd + 2; // 跳过]]
        }
        return result.toString();
    }

    /**
     * 用 MiniMax-VL-01 提取图片描述（参考OpenClaw实现）
     * 端点: POST /v1/coding_plan/vlm
     * 基地址: https://api.minimax.io/anthropic
     */
    private String extractImageDescription(String base64, String format) {
        try {
            String compressedBase64 = compressImage(base64, format);
            String imageDataUrl = "data:image/" + format + ";base64," + compressedBase64;
            
            // VL模型使用专用基地址（参考OpenClaw）
            String url = VL_BASE_URL + "/v1/coding_plan/vlm";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", "请描述这张图片，用简洁的语言说明图片内容。");
            requestBody.put("image_url", imageDataUrl);

            String jsonBody = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(requestBody);

            log.info("[MiniMax-VL] 调用视觉模型提取图片描述, endpoint={}", url);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("MM-API-Source", "OpenClaw")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(60))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.info("[MiniMax-VL] 响应状态: {}, body: {}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                log.error("MiniMax VL API错误: {} - {}", response.statusCode(), response.body());
                return "（图片识别失败）";
            }

            Map<String, Object> respMap = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(response.body(), Map.class);

            // 检查 base_resp 错误
            if (respMap.containsKey("base_resp")) {
                Map<String, Object> baseResp = (Map<String, Object>) respMap.get("base_resp");
                int statusCode = ((Number) baseResp.get("status_code")).intValue();
                if (statusCode != 0) {
                    String statusMsg = (String) baseResp.get("status_msg");
                    log.error("MiniMax VL API错误: status_code={}, msg={}", statusCode, statusMsg);
                    return "（图片识别失败: " + statusMsg + "）";
                }
            }

            // 解析 content 字段
            Object contentObj = respMap.get("content");
            if (contentObj != null) {
                String content = contentObj.toString().trim();
                if (!content.isEmpty()) {
                    log.info("[MiniMax-VL] 图片描述: {}", content);
                    return content;
                }
            }
            
            return "（图片描述为空）";
            
        } catch (Exception e) {
            log.error("提取图片描述失败", e);
            return "（图片识别失败: " + e.getMessage() + "）";
        }
    }

    private List<Object> toMiniMaxMessages(List<Memory.Message> messages) {
        List<Object> result = new ArrayList<>();
        for (Memory.Message msg : messages) {
            Map<String, Object> m = new HashMap<>();
            m.put("role", toMiniMaxRole(msg.role()));
            m.put("content", msg.content());
            result.add(m);
        }
        return result;
    }

    /**
     * MiniMax-M2.7 原生多模态消息格式
     * 图片直接作为 content 数组中的对象发送，不需要先提取描述
     */
    private List<Object> toMiniMaxMessagesWithImages(List<Memory.Message> messages) {
        List<Object> result = new ArrayList<>();
        for (Memory.Message msg : messages) {
            if (msg.hasImage()) {
                // 多模态格式：content 是数组，包含 text 和 image 对象
                List<Object> contentList = new ArrayList<>();
                for (Memory.ContentItem item : msg.getContentItems()) {
                    if (item instanceof Memory.ContentItem.Text t) {
                        contentList.add(Map.of("type", "text", "text", t.text()));
                    } else if (item instanceof Memory.ContentItem.Image img) {
                        // 压缩图片以减少 token 消耗
                        String compressedBase64 = compressImage(img.base64(), img.format());
                        contentList.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", "data:image/" + img.format() + ";base64," + compressedBase64)
                        ));
                    }
                }
                result.add(Map.of(
                    "role", toMiniMaxRole(msg.role()),
                    "content", contentList
                ));
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

    /**
     * 压缩图片base64，限制最大边为512像素，使用JPEG 0.7质量
     */
    private String compressImage(String base64, String format) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64);
            java.io.InputStream is = new java.io.ByteArrayInputStream(imageBytes);
            BufferedImage originalImage = ImageIO.read(is);
            is.close();

            int maxSize = 512; // 限制最大边为512像素
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width <= maxSize && height <= maxSize) {
                return base64; // 不需要压缩
            }

            double ratio = Math.min((double) maxSize / width, (double) maxSize / height);
            int newWidth = (int) (width * ratio);
            int newHeight = (int) (height * ratio);

            BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resizedImage.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g.dispose();

            // 使用JPEG 0.7质量压缩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.7f);
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new javax.imageio.IIOImage(resizedImage, null, null), param);
            writer.dispose();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.warn("图片压缩失败，使用原图: {}", e.getMessage());
            return base64;
        }
    }
}
