package com.agent.core.agent;

import com.agent.core.memory.ConversationMemory;
import com.agent.core.memory.Memory;
import com.agent.core.model.ChatModel;
import com.agent.core.model.ModelResponse;
import com.agent.core.tool.Tool;
import com.agent.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReActAgent单元测试
 */
class ReActAgentTest {

    private ReActAgent agent;

    @BeforeEach
    void setUp() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new TestTool("test", "测试工具"));

        agent = ReActAgent.builder()
            .name("test-agent")
            .description("测试Agent")
            .systemPrompt("你是一个测试助手。")
            .toolRegistry(registry)
            .maxSteps(5)
            .build();
    }

    @Test
    @DisplayName("测试Agent基本属性")
    void testAgentProperties() {
        assertEquals("test-agent", agent.getName());
        assertEquals("测试Agent", agent.getDescription());
        assertEquals(1, agent.getTools().size());
    }

    @Test
    @DisplayName("测试添加工具")
    void testAddTool() {
        agent.addTool(new TestTool("test2", "测试工具2"));
        assertEquals(2, agent.getTools().size());
    }

    @Test
    @DisplayName("测试移除工具")
    void testRemoveTool() {
        agent.removeTool("test");
        assertEquals(0, agent.getTools().size());
    }

    /**
     * Mock ChatModel实现
     */
    static class MockChatModel implements ChatModel {
        @Override
        public ModelResponse chat(List<Memory.Message> messages) {
            return ModelResponse.builder()
                .content("这是一个测试响应")
                .done(true)
                .model("mock-model")
                .build();
        }

        @Override
        public ModelResponse chat(List<Memory.Message> messages, Map<String, Object> parameters) {
            return chat(messages);
        }

        @Override
        public String getModelName() {
            return "mock-model";
        }
    }

    /**
     * 测试用工具
     */
    static class TestTool implements Tool {
        private final String name;
        private final String description;

        TestTool(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() { return name; }
        @Override
        public String getDescription() { return description; }
        @Override
        public String getParameterSchema() { return "{}"; }
        @Override
        public ToolResult execute(Map<String, Object> parameters) {
            return ToolResult.success("test result");
        }
    }
}
