package com.agent;

import com.agent.core.tool.SearchTool;
import com.agent.core.tool.CalculatorTool;
import com.agent.core.tool.DateTimeTool;
import com.agent.core.tool.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具测试
 */
class ToolTest {

    @Test
    @DisplayName("测试SearchTool")
    void testSearchTool() {
        SearchTool tool = new SearchTool();
        
        assertEquals("search", tool.getName());
        assertNotNull(tool.getDescription());
        assertNotNull(tool.getParameterSchema());
        
        Tool.ToolResult result = tool.execute(Map.of("query", "Java"));
        assertTrue(result.success());
        assertNotNull(result.output());
    }

    @Test
    @DisplayName("测试CalculatorTool")
    void testCalculatorTool() {
        CalculatorTool tool = new CalculatorTool();
        
        assertEquals("calculator", tool.getName());
        
        Tool.ToolResult result = tool.execute(Map.of("expression", "2+2"));
        assertTrue(result.success());
        assertNotNull(result.output());
        // 2+2 = 4, 可能返回"4"或"4.0"
        assertTrue(result.output().contains("4"));
    }

    @Test
    @DisplayName("测试CalculatorTool根号运算")
    void testCalculatorSqrt() {
        CalculatorTool tool = new CalculatorTool();
        
        Tool.ToolResult result = tool.execute(Map.of("expression", "sqrt(16)"));
        assertTrue(result.success());
        assertTrue(result.output().contains("4"));
    }

    @Test
    @DisplayName("测试DateTimeTool")
    void testDateTimeTool() {
        DateTimeTool tool = new DateTimeTool();
        
        assertEquals("datetime", tool.getName());
        
        Tool.ToolResult result = tool.execute(Map.of());
        assertTrue(result.success());
        assertNotNull(result.output());
    }

    @Test
    @DisplayName("测试ToolResult工厂方法")
    void testToolResultFactory() {
        Tool.ToolResult success = Tool.ToolResult.success("ok");
        assertTrue(success.success());
        assertEquals("ok", success.output());
        assertNull(success.error());

        Tool.ToolResult failure = Tool.ToolResult.failure("error");
        assertFalse(failure.success());
        assertNull(failure.output());
        assertEquals("error", failure.error());
    }
}
