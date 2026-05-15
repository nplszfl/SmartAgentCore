package com.agent.service.impl;

import com.agent.entity.ConversationEntity;
import com.agent.mapper.ConversationMapper;
import com.agent.service.ConversationService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话会话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public ConversationEntity getOrCreateConversation(String userId, String agentName, String systemPrompt) {
        // 查询用户最近的会话
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where("user_id = '" + userId + "'");
        wrapper.orderBy("update_time", false);
        wrapper.limit(1);

        ConversationEntity existing = conversationMapper.selectOneByQuery(wrapper);

        if (existing != null) {
            // 更新访问时间
            jdbcTemplate.update("UPDATE conversation SET update_time = ? WHERE id = ?",
                LocalDateTime.now(), existing.getId());
            existing.setUpdateTime(LocalDateTime.now());
            return existing;
        }

        // 创建新会话
        ConversationEntity newConversation = new ConversationEntity();
        newConversation.setUserId(userId);
        newConversation.setAgentName(agentName);
        newConversation.setSystemPrompt(systemPrompt);
        newConversation.setMessages("[]");
        newConversation.setCreateTime(LocalDateTime.now());
        newConversation.setUpdateTime(LocalDateTime.now());

        conversationMapper.insert(newConversation);
        log.info("[Conversation] 创建新会话 userId={}, id={}", userId, newConversation.getId());

        return newConversation;
    }

    @Override
    public void updateMessages(Long conversationId, String messagesJson) {
        jdbcTemplate.update("UPDATE conversation SET messages = ?, update_time = ? WHERE id = ?",
            messagesJson, LocalDateTime.now(), conversationId);
        log.debug("[Conversation] 更新消息 conversationId={}", conversationId);
    }

    @Override
    public List<ConversationEntity> getUserConversations(String userId) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where("user_id = '" + userId + "'");
        wrapper.orderBy("update_time", false);

        return conversationMapper.selectListByQuery(wrapper);
    }
}
