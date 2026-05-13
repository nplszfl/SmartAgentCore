package com.agent.core.tool;

import java.util.Map;

/**
 * 日期时间工具 - 内置工具示例
 */
public class DateTimeTool implements Tool {

    private final String name = "datetime";
    private final String description = "获取当前日期时间和执行日期时间相关的操作。";

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getParameterSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "format": {
                    "type": "string",
                    "description": "日期格式，默认: yyyy-MM-dd HH:mm:ss",
                    "default": "yyyy-MM-dd HH:mm:ss"
                },
                "timezone": {
                    "type": "string",
                    "description": "时区，如: Asia/Shanghai, America/New_York",
                    "default": "Asia/Shanghai"
                }
            }
        }
        """;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        try {
            String format = parameters.containsKey("format") 
                ? (String) parameters.get("format") 
                : "yyyy-MM-dd HH:mm:ss";
            String timezone = parameters.containsKey("timezone")
                ? (String) parameters.get("timezone")
                : "Asia/Shanghai";

            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneId.of(timezone));
            String result = now.format(java.time.format.DateTimeFormatter.ofPattern(format));

            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure("获取日期时间失败: " + e.getMessage());
        }
    }
}
