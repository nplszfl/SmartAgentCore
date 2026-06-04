package com.agent;

import com.agent.entity.AgentTemplate;
import com.agent.service.impl.AgentTemplateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentTemplateService 业务功能测试
 */
class AgentTemplateServiceTest {

    private AgentTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentTemplateServiceImpl();
        service.init();
    }

    @Test
    @DisplayName("内置模板已加载")
    void builtInTemplatesLoaded() {
        List<AgentTemplate> all = service.listAll();
        assertTrue(all.size() >= 6, "至少 6 个内置模板，实际: " + all.size());

        assertTrue(service.getById("general-assistant").isPresent());
        assertTrue(service.getById("sql-expert").isPresent());
        assertTrue(service.getById("code-reviewer").isPresent());
        assertTrue(service.getById("translator").isPresent());
    }

    @Test
    @DisplayName("按分类过滤")
    void filterByCategory() {
        List<AgentTemplate> code = service.listByCategory("code");
        assertEquals(2, code.size());
        assertTrue(code.stream().allMatch(t -> "code".equals(t.getCategory())));
    }

    @Test
    @DisplayName("按 ID 获取")
    void getById() {
        Optional<AgentTemplate> t = service.getById("sql-expert");
        assertTrue(t.isPresent());
        assertEquals("SQL 专家", t.get().getName());
        assertNotNull(t.get().getSystemPrompt());
        assertNotNull(t.get().getTools());
    }

    @Test
    @DisplayName("搜索模板（名称）")
    void searchByName() {
        List<AgentTemplate> results = service.search("SQL");
        assertTrue(results.size() >= 1);
        assertTrue(results.stream().anyMatch(t -> "sql-expert".equals(t.getId())));
    }

    @Test
    @DisplayName("搜索模板（描述）")
    void searchByDescription() {
        List<AgentTemplate> results = service.search("翻译");
        assertTrue(results.size() >= 1);
    }

    @Test
    @DisplayName("空关键字返回全部")
    void emptyKeyword() {
        assertEquals(service.listAll().size(), service.search("").size());
    }

    @Test
    @DisplayName("记录使用次数")
    void recordUsage() {
        long before = service.getById("general-assistant").get().getUsageCount();
        service.recordUsage("general-assistant");
        service.recordUsage("general-assistant");
        long after = service.getById("general-assistant").get().getUsageCount();
        assertEquals(before + 2, after);
    }

    @Test
    @DisplayName("最常用模板排序")
    void mostUsed() {
        for (int i = 0; i < 5; i++) service.recordUsage("general-assistant");
        for (int i = 0; i < 2; i++) service.recordUsage("sql-expert");
        List<AgentTemplate> top = service.mostUsed(2);
        assertEquals("general-assistant", top.get(0).getId());
        assertTrue(top.get(0).getUsageCount() > top.get(1).getUsageCount());
    }

    @Test
    @DisplayName("注册自定义模板")
    void registerCustom() {
        AgentTemplate custom = AgentTemplate.builder()
            .id("custom-1")
            .name("自定义助手")
            .category("custom")
            .description("测试")
            .systemPrompt("你是测试助手")
            .tools(List.of())
            .build();
        AgentTemplate saved = service.register(custom);
        assertNotNull(saved);
        assertTrue(service.getById("custom-1").isPresent());
    }

    @Test
    @DisplayName("注册模板必须提供 ID")
    void registerRequiresId() {
        AgentTemplate bad = AgentTemplate.builder().name("无 ID").build();
        assertThrows(IllegalArgumentException.class, () -> service.register(bad));
    }

    @Test
    @DisplayName("移除模板")
    void removeTemplate() {
        service.register(AgentTemplate.builder().id("custom-temp").name("临时").build());
        assertTrue(service.remove("custom-temp"));
        assertFalse(service.remove("non-existent"));
    }

    @Test
    @DisplayName("分类列表去重排序")
    void categoriesList() {
        List<String> cats = service.categories();
        assertTrue(cats.contains("general"));
        assertTrue(cats.contains("code"));
        assertTrue(cats.contains("writing"));
        // 排序
        for (int i = 1; i < cats.size(); i++) {
            assertTrue(cats.get(i - 1).compareTo(cats.get(i)) <= 0);
        }
    }

    @Test
    @DisplayName("模板含推荐参数")
    void recommendedParams() {
        AgentTemplate t = service.getById("sql-expert").get();
        assertNotNull(t.getRecommendedParams());
        assertEquals(0.2, ((Number) t.getRecommendedParams().get("temperature")).doubleValue(), 0.001);
    }
}
