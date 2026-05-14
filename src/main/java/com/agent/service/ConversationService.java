package com.agent.service;

import com.agent.entity.ConversationEntity;

/**
 * 对话会话服务接口
 */
public interface ConversationService {

    /**
     * 根据用户ID获取或创建会话
     */
    ConversationEntity getOrCreateConversation(String userId, String agentName, String systemPrompt);

    /**
     * 更新会话消息历史
     */
    void updateMessages(Long conversationId, String messagesJson);

    /**
     * 获取用户会话列表
     */
    java.util.List<ConversationEntity> getUserConversations(String userId);
}
