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
     * 消息内容项 - 支持文本和图片
     */
    sealed interface ContentItem {
        record Text(String text) implements ContentItem {}
        record Image(String base64, String format) implements ContentItem {}
    }

    /**
     * 消息结构 - 支持多模态内容
     */
    record Message(Role role, Object content, String name) {
        // content 可以是 String（纯文本）或 List<ContentItem>（多模态）
        public Message(Role role, String content) {
            this(role, (Object) content, null);
        }

        public Message(Role role, String content, String name) {
            this(role, (Object) content, name);
        }

        public Message(Role role, List<ContentItem> content) {
            this(role, (Object) content, null);
        }

        public boolean hasImage() {
            if (content instanceof List<?> list) {
                return list.stream().anyMatch(i -> i instanceof ContentItem.Image);
            }
            return false;
        }

        public List<ContentItem> getContentItems() {
            if (content instanceof List<?> list) {
                return list.stream().map(i -> (ContentItem) i).toList();
            }
            return List.of(new ContentItem.Text((String) content));
        }

        public enum Role {
            SYSTEM, USER, ASSISTANT, TOOL
        }
    }
}
