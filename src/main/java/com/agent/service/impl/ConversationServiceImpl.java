package com.agent.service.impl;

import com.agent.entity.ConversationEntity;
import com.agent.mapper.ConversationMapper;
import com.agent.service.ConversationService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话会话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;

    @Override
    public ConversationEntity getOrCreateConversation(String userId, String agentName, String systemPrompt) {
        // 查询用户最近的会话
        QueryWrapper wrapper = QueryWrapper.create().where("user_id = ?", userId)
                .orderBy("update_time", false)
                .limit(1);

        ConversationEntity existing = conversationMapper.selectOneByQuery(wrapper);

        if (existing != null) {
            // 更新访问时间
            existing.setUpdateTime(java.time.LocalDateTime.now());
            conversationMapper.update(existing);
            return existing;
        }

        // 创建新会话
        ConversationEntity newConversation = new ConversationEntity();
        newConversation.setUserId(userId);
        newConversation.setAgentName(agentName);
        newConversation.setSystemPrompt(systemPrompt);
        newConversation.setMessages("[]");
        newConversation.setCreateTime(java.time.LocalDateTime.now());
        newConversation.setUpdateTime(java.time.LocalDateTime.now());

        conversationMapper.insert(newConversation);
        log.info("[Conversation] 创建新会话 userId={}, id={}", userId, newConversation.getId());

        return newConversation;
    }

    @Override
    public void updateMessages(Long conversationId, String messagesJson) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(conversationId);
        entity.setMessages(messagesJson);
        entity.setUpdateTime(java.time.LocalDateTime.now());
        conversationMapper.update(entity);
        log.debug("[Conversation] 更新消息 conversationId={}", conversationId);
    }

    @Override
    public List<ConversationEntity> getUserConversations(String userId) {
        QueryWrapper wrapper = QueryWrapper.create().where("user_id = ?", userId)
                .orderBy("update_time", false);

        return conversationMapper.selectListByQuery(wrapper);
    }
}
