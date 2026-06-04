package com.agent.service;

import com.agent.entity.AgentTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Agent 模板服务
 *
 * 为平台提供开箱即用的 Agent 模板，覆盖常见业务场景。
 * 支持：
 *  - 列出所有模板 / 按分类列出
 *  - 搜索（按名称/描述/标签）
 *  - 记录模板使用次数
 *  - 注册自定义模板
 */
public interface AgentTemplateService {

    /**
     * 列出所有内置模板
     */
    List<AgentTemplate> listAll();

    /**
     * 按分类列出模板
     */
    List<AgentTemplate> listByCategory(String category);

    /**
     * 按 ID 获取模板
     */
    Optional<AgentTemplate> getById(String id);

    /**
     * 搜索模板（name/description 模糊匹配）
     */
    List<AgentTemplate> search(String keyword);

    /**
     * 获取最常用的模板
     */
    List<AgentTemplate> mostUsed(int limit);

    /**
     * 记录一次模板使用（自增 usageCount）
     */
    void recordUsage(String templateId);

    /**
     * 注册自定义模板
     */
    AgentTemplate register(AgentTemplate template);

    /**
     * 删除自定义模板
     */
    boolean remove(String id);

    /**
     * 所有分类
     */
    List<String> categories();
}
