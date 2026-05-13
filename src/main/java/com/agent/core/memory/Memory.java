package com.agent.core.memory;

import java.util.List;

/**
 * 记忆接口
 * Memory enables Agents to remember conversation history
 */
public interface Memory {

    /**
     * 添加消息到记忆
     */
    void addMessage(Message message);

    /**
     * 添加用户消息
     */
    default void addUserMessage(String content) {
        addMessage(new Message(Message.Role.USER, content));
    }

    /**
     * 添加助手消息
     */
    default void addAssistantMessage(String content) {
        addMessage(new Message(Message.Role.ASSISTANT, content));
    }

    /**
     * 添加系统消息
     */
    default void addSystemMessage(String content) {
        addMessage(new Message(Message.Role.SYSTEM, content));
    }

    /**
     * 获取所有消息
     */
    List<Message> getMessages();

    /**
     * 获取最近N条消息
     */
    List<Message> getRecentMessages(int count);

    /**
     * 清空记忆
     */
    void clear();

    /**
     * 消息结构
     */
    record Message(Role role, String content, String name) {
        public Message(Role role, String content) {
            this(role, content, null);
        }

        public enum Role {
            SYSTEM, USER, ASSISTANT, TOOL
        }
    }
}
