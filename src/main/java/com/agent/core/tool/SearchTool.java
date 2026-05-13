package com.agent.core.tool;

import java.util.Map;

/**
 * 搜索工具 - 内置工具示例
 */
public class SearchTool implements Tool {

    private final String name = "search";
    private final String description = "搜索互联网获取信息。适用于查询实时数据、新闻、天气等信息。";
    private final String parameterSchema = """
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词"
                },
                "limit": {
                    "type": "integer",
                    "description": "返回结果数量",
                    "default": 5
                }
            },
            "required": ["query"]
        }
        """;

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
        return parameterSchema;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        try {
            String query = (String) parameters.get("query");
            if (query == null || query.isBlank()) {
                return ToolResult.failure("搜索关键词不能为空");
            }

            // 这里可以集成真实的搜索API，如Google、Bing等
            // 暂时返回模拟数据
            String result = String.format(
                "搜索结果 for \"%s\":\n1. 相关结果A - 简要描述...\n2. 相关结果B - 简要描述...\n3. 相关结果C - 简要描述...",
                query
            );

            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }
}
