package com.agent.service.impl;

import com.agent.entity.ConversationEntity;
import com.agent.mapper.ConversationMapper;
import com.agent.service.ConversationService;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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

    // MyBatis-Flex 字段引用
    private static final QueryColumn ID = new QueryColumn("id");
    private static final QueryColumn USER_ID = new QueryColumn("user_id");
    private static final QueryColumn UPDATE_TIME = new QueryColumn("update_time");
    private static final QueryColumn MESSAGES = new QueryColumn("messages");

    @Override
    public ConversationEntity getOrCreateConversation(String userId, String agentName, String systemPrompt) {
        // 查询用户最近的会话 - MyBatis-Flex eq() 直接传值，会自动处理字符串参数
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where(USER_ID.eq(userId));
        wrapper.orderBy(UPDATE_TIME, false);
        wrapper.limit(1);

        ConversationEntity existing = conversationMapper.selectOneByQuery(wrapper);

        if (existing != null) {
            // 更新访问时间 - 使用 UpdateChain
            UpdateChain.of(conversationMapper)
                    .set(UPDATE_TIME, LocalDateTime.now())
                    .where(ID.eq(existing.getId()))
                    .update();
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
        // 更新消息内容 - 使用 UpdateChain
        UpdateChain.of(conversationMapper)
                .set(MESSAGES, messagesJson)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(ID.eq(conversationId))
                .update();
        log.debug("[Conversation] 更新消息 conversationId={}", conversationId);
    }

    @Override
    public List<ConversationEntity> getUserConversations(String userId) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where(USER_ID.eq(userId));
        wrapper.orderBy(UPDATE_TIME, false);

        return conversationMapper.selectListByQuery(wrapper);
    }

    @Override
    public ConversationEntity getConversation(Long id) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where(ID.eq(id));
        return conversationMapper.selectOneByQuery(wrapper);
    }

    @Override
    public void deleteConversation(Long id) {
        conversationMapper.deleteById(id);
        log.info("[Conversation] 删除会话 id={}", id);
    }

    @Override
    public void clearUserConversations(String userId) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where(USER_ID.eq(userId));
        conversationMapper.deleteByQuery(wrapper);
        log.info("[Conversation] 清空用户所有会话 userId={}", userId);
    }

    /**
     * 定期清理7天未活跃的会话，防止数据积累
     * 每天凌晨3点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupStaleConversations() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.where(UPDATE_TIME.lt(threshold));
        int deleted = conversationMapper.deleteByQuery(wrapper);
        if (deleted > 0) {
            log.info("[Conversation] 清理过期会话 {} 条", deleted);
        }
    }
}
