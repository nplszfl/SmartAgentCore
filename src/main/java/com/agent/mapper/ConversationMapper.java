package com.agent.mapper;

import com.agent.entity.ConversationEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对话会话 Mapper
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationEntity> {
}
