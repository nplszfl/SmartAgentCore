package com.agent.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 对话会话实体
 */
@Data
@TableName("conversation")
public class ConversationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 对话历史（JSON格式存储）
     */
    private String messages;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
