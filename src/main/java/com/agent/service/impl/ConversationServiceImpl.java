package com.agent.service.impl;

import com.agent.entity.ConversationEntity;
import com.agent.mapper.ConversationMapper;
import com.agent.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
        LambdaQueryWrapper<ConversationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationEntity::getUserId, userId)
               .orderByDesc(ConversationEntity::getUpdateTime)
               .last("LIMIT 1");

        ConversationEntity existing = conversationMapper.selectOne(wrapper);

        if (existing != null) {
            // 更新访问时间
            existing.setUpdateTime(java.time.LocalDateTime.now());
            conversationMapper.updateById(existing);
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
        LambdaUpdateWrapper<ConversationEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ConversationEntity::getId, conversationId)
               .set(ConversationEntity::getMessages, messagesJson)
               .set(ConversationEntity::getUpdateTime, java.time.LocalDateTime.now());

        conversationMapper.update(null, wrapper);
        log.debug("[Conversation] 更新消息 conversationId={}", conversationId);
    }

    @Override
    public List<ConversationEntity> getUserConversations(String userId) {
        LambdaQueryWrapper<ConversationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConversationEntity::getUserId, userId)
               .orderByDesc(ConversationEntity::getUpdateTime);

        return conversationMapper.selectList(wrapper);
    }
}
