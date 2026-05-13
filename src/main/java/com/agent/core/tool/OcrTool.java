package com.agent.core.tool;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/**
 * OCR工具 - 识别图片中的文字
 */
@Slf4j
public class OcrTool implements Tool {

    private final HttpClient httpClient;
    private final String apiKey;

    public OcrTool() {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = null; // 使用免费的Tesseract方式或本地OCR
    }

    public OcrTool(String apiKey) {
        this.httpClient = HttpClient.newHttpClient();
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "ocr";
    }

    @Override
    public String getDescription() {
        return "OCR文字识别工具，可以从图片中提取文字。支持本地图片路径或网络图片URL。";
    }

    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "image": {
                        "type": "string",
                        "description": "图片路径或URL"
                    }
                },
                "required": ["image"]
            }""";
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        try {
            String imagePath = parameters.get("image").toString();
            
            // 判断是URL还是本地文件
            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                return ocrFromUrl(imagePath);
            } else {
                return ocrFromFile(imagePath);
            }
        } catch (Exception e) {
            log.error("OCR执行失败", e);
            return ToolResult.failure("OCR识别失败: " + e.getMessage());
        }
    }

    /**
     * 从URL识别图片文字
     */
    private ToolResult ocrFromUrl(String imageUrl) {
        try {
            // 使用免费OCR.space API (无需API Key, 有免费额度限制)
            String apiUrl = "https://api.ocr.space/parse/image";
            
            String requestBody = """
                {
                    "url": "%s",
                    "language": "chs",
                    "isOverlayRequired": false,
                    "detectOrientation": true,
                    "scale": true,
                    "OCREngine": 2
                }
                """.formatted(imageUrl);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("apikey", apiKey != null ? apiKey : "helloworld") // 免费API Key
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseOcrResponse(response.body());
            } else {
                return ToolResult.failure("OCR API错误: " + response.statusCode());
            }
        } catch (Exception e) {
            return ToolResult.failure("URL OCR失败: " + e.getMessage());
        }
    }

    /**
     * 从本地文件识别文字
     */
    private ToolResult ocrFromFile(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return ToolResult.failure("文件不存在: " + filePath);
            }

            // 读取文件并转为Base64
            byte[] imageBytes = Files.readAllBytes(path);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 使用OCR.space API
            String apiUrl = "https://api.ocr.space/parse/image";
            
            // 构建multipart form请求 (简化版用URLencode)
            String requestBody = """
                {
                    "base64Image": "data:image/png;base64,%s",
                    "language": "chs",
                    "isOverlayRequired": false,
                    "detectOrientation": true,
                    "scale": true,
                    "OCREngine": 2
                }
                """.formatted(base64Image);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("apikey", apiKey != null ? apiKey : "helloworld")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(java.time.Duration.ofSeconds(60))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return parseOcrResponse(response.body());
            } else {
                return ToolResult.failure("OCR API错误: " + response.statusCode());
            }
        } catch (Exception e) {
            return ToolResult.failure("本地文件OCR失败: " + e.getMessage());
        }
    }

    /**
     * 解析OCR API响应
     */
    private ToolResult parseOcrResponse(String jsonResponse) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> resp = mapper.readValue(jsonResponse, Map.class);
            
            if (Boolean.TRUE.equals(resp.get("IsErroredOnProcessing"))) {
                return ToolResult.failure("OCR处理出错");
            }

            var parsedResults = (java.util.List<Map<String, Object>>) resp.get("ParsedResults");
            if (parsedResults != null && !parsedResults.isEmpty()) {
                StringBuilder text = new StringBuilder();
                for (Map<String, Object> result : parsedResults) {
                    String textOverlay = (String) result.get("ParsedText");
                    text.append(textOverlay).append("\n");
                }
                String extractedText = text.toString().trim();
                if (extractedText.isEmpty()) {
                    return ToolResult.success("未识别到文字");
                }
                return ToolResult.success(extractedText);
            }

            return ToolResult.success("未识别到文字");
        } catch (Exception e) {
            return ToolResult.failure("解析OCR响应失败: " + e.getMessage());
        }
    }
}
