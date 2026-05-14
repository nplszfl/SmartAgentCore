-- SmartAgent Core 数据库表结构
-- 创建数据库: CREATE DATABASE IF NOT EXISTS smartagent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 对话会话表
CREATE TABLE IF NOT EXISTS `conversation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `agent_name` VARCHAR(128) DEFAULT 'assistant' COMMENT 'Agent名称',
    `system_prompt` TEXT COMMENT '系统提示词',
    `messages` LONGTEXT COMMENT '对话历史JSON',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';
