package com.agent.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationMemory单元测试
 */
class ConversationMemoryTest {

    private ConversationMemory memory;

    @BeforeEach
    void setUp() {
        memory = new ConversationMemory();
    }

    @Test
    @DisplayName("测试添加和获取消息")
    void testAddAndGetMessages() {
        memory.addUserMessage("Hello");
        memory.addAssistantMessage("Hi there");

        List<Memory.Message> messages = memory.getMessages();
        assertEquals(2, messages.size());
        assertEquals(Memory.Message.Role.USER, messages.get(0).role());
        assertEquals("Hello", messages.get(0).content());
    }

    @Test
    @DisplayName("测试获取最近消息")
    void testGetRecentMessages() {
        for (int i = 0; i < 10; i++) {
            memory.addUserMessage("Message " + i);
        }

        List<Memory.Message> recent = memory.getRecentMessages(3);
        assertEquals(3, recent.size());
        assertEquals("Message 7", recent.get(0).content());
    }

    @Test
    @DisplayName("测试清空记忆")
    void testClear() {
        memory.addUserMessage("Hello");
        memory.clear();

        assertEquals(0, memory.getMessages().size());
    }

    @Test
    @DisplayName("测试最大容量限制")
    void testMaxSize() {
        ConversationMemory limitedMemory = new ConversationMemory(5);
        for (int i = 0; i < 10; i++) {
            limitedMemory.addUserMessage("Message " + i);
        }

        assertEquals(5, limitedMemory.size());
        List<Memory.Message> messages = limitedMemory.getMessages();
        assertEquals("Message 5", messages.get(0).content());
    }
}
