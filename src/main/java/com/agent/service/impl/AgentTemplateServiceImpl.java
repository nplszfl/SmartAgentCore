package com.agent.service.impl;

import com.agent.entity.AgentTemplate;
import com.agent.service.AgentTemplateService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 模板服务实现
 *
 * 启动时初始化常用模板（通用助手、SQL 专家、代码审查、翻译官、文案写手、数据分析师等）。
 * 使用 ConcurrentHashMap 存储，支持运行时注册和移除自定义模板。
 */
@Slf4j
@Service
public class AgentTemplateServiceImpl implements AgentTemplateService {

    private final Map<String, AgentTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        registerBuiltIn("general-assistant", AgentTemplate.builder()
            .id("general-assistant")
            .name("通用助手")
            .category("general")
            .icon("🤖")
            .description("能回答问题、调用工具的通用智能助手，适用于日常问答和任务执行。")
            .systemPrompt("你是一个智能助手，会根据需要使用工具来获取信息或执行操作。" +
                "回答时要简洁准确，不确定时主动调用工具。")
            .tools(List.of("search", "datetime", "calculator"))
            .maxIterations(8)
            .recommendedModel("MiniMax-M2")
            .recommendedParams(Map.of("temperature", 0.7, "maxTokens", 2048))
            .build());

        registerBuiltIn("sql-expert", AgentTemplate.builder()
            .id("sql-expert")
            .name("SQL 专家")
            .category("code")
            .icon("🗄️")
            .description("专业的 SQL 工程师，擅长编写、优化和解释复杂 SQL 查询。")
            .systemPrompt("你是一位资深 SQL 工程师，擅长编写和优化 SQL 查询。" +
                "请遵循以下原则：1) 给出可执行的 SQL 2) 解释执行计划 3) 提供优化建议 4) 注意 SQL 注入风险。")
            .tools(List.of("datetime", "calculator"))
            .maxIterations(5)
            .recommendedModel("deepseek-coder")
            .recommendedParams(Map.of("temperature", 0.2, "maxTokens", 2048))
            .build());

        registerBuiltIn("code-reviewer", AgentTemplate.builder()
            .id("code-reviewer")
            .name("代码审查员")
            .category("code")
            .icon("🔍")
            .description("资深软件工程师，专注于代码质量、潜在缺陷和最佳实践。")
            .systemPrompt("你是一位资深的代码审查工程师，专注代码质量、性能和安全。" +
                "审查代码时关注：1) 正确性 2) 可读性 3) 性能 4) 安全性 5) 可维护性。" +
                "输出结构化建议：严重程度、问题描述、改进代码。")
            .tools(List.of())
            .maxIterations(3)
            .recommendedModel("deepseek-coder")
            .recommendedParams(Map.of("temperature", 0.3, "maxTokens", 3000))
            .build());

        registerBuiltIn("translator", AgentTemplate.builder()
            .id("translator")
            .name("翻译官")
            .category("writing")
            .icon("🌐")
            .description("专业翻译，支持中英日韩等多语种互译，保留原文语气和格式。")
            .systemPrompt("你是一位专业翻译，精通中英日韩法德俄等多国语言。" +
                "翻译时保留原文的语气、专业术语、格式（Markdown/代码）。" +
                "对模糊表达先询问再翻译。")
            .tools(List.of())
            .maxIterations(2)
            .recommendedModel("MiniMax-M2")
            .recommendedParams(Map.of("temperature", 0.3, "maxTokens", 2048))
            .build());

        registerBuiltIn("copywriter", AgentTemplate.builder()
            .id("copywriter")
            .name("文案写手")
            .category("writing")
            .icon("✍️")
            .description("营销文案、公众号文章、产品描述、广告语创作专家。")
            .systemPrompt("你是一位资深文案写手，擅长撰写营销文案、公众号文章、产品介绍。" +
                "风格可调：活泼/正式/幽默/感人。输出结构：标题 + 钩子 + 正文 + CTA。")
            .tools(List.of("datetime", "search"))
            .maxIterations(5)
            .recommendedModel("MiniMax-M2")
            .recommendedParams(Map.of("temperature", 0.9, "maxTokens", 2048))
            .build());

        registerBuiltIn("data-analyst", AgentTemplate.builder()
            .id("data-analyst")
            .name("数据分析师")
            .category("analysis")
            .icon("📊")
            .description("擅长从数据中发现趋势、异常，并给出可操作的业务建议。")
            .systemPrompt("你是一位资深数据分析师。分析数据时：1) 描述统计特征 2) 识别趋势/异常" +
                " 3) 给出可验证的假设 4) 转化为业务建议。结构化输出。")
            .tools(List.of("calculator", "datetime"))
            .maxIterations(6)
            .recommendedModel("deepseek-coder")
            .recommendedParams(Map.of("temperature", 0.4, "maxTokens", 2500))
            .build());

        registerBuiltIn("customer-support", AgentTemplate.builder()
            .id("customer-support")
            .name("客服助手")
            .category("support")
            .icon("💬")
            .description("耐心专业的客服助手，能安抚情绪、解答问题、必要时升级人工。")
            .systemPrompt("你是一位耐心的客服代表。原则：1) 先共情再解决问题 2) 不确定时诚实告知" +
                " 3) 给出可执行步骤 4) 必要时建议联系人工。")
            .tools(List.of("search", "datetime"))
            .maxIterations(5)
            .recommendedModel("MiniMax-M2")
            .recommendedParams(Map.of("temperature", 0.5, "maxTokens", 1500))
            .build());

        registerBuiltIn("tech-writer", AgentTemplate.builder()
            .id("tech-writer")
            .name("技术文档员")
            .category("writing")
            .icon("📝")
            .description("撰写 API 文档、技术方案、用户手册。")
            .systemPrompt("你是一位资深技术文档作者。撰写技术文档时：1) 目标读者明确 2) 结构清晰" +
                " 3) 含代码示例 4) 标注注意事项。格式标准 Markdown。")
            .tools(List.of())
            .maxIterations(3)
            .recommendedModel("deepseek-coder")
            .recommendedParams(Map.of("temperature", 0.3, "maxTokens", 3000))
            .build());

        log.info("[AgentTemplate] 已注册 {} 个内置模板", templates.size());
    }

    private void registerBuiltIn(String id, AgentTemplate template) {
        templates.put(id, template);
    }

    @Override
    public List<AgentTemplate> listAll() {
        return templates.values().stream()
            .sorted(Comparator.comparingLong(AgentTemplate::getUsageCount).reversed()
                .thenComparing(AgentTemplate::getName))
            .collect(Collectors.toList());
    }

    @Override
    public List<AgentTemplate> listByCategory(String category) {
        if (category == null) return listAll();
        return templates.values().stream()
            .filter(t -> category.equalsIgnoreCase(t.getCategory()))
            .sorted(Comparator.comparingLong(AgentTemplate::getUsageCount).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public Optional<AgentTemplate> getById(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public List<AgentTemplate> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return listAll();
        String kw = keyword.toLowerCase();
        return templates.values().stream()
            .filter(t -> containsIgnoreCase(t.getName(), kw)
                || containsIgnoreCase(t.getDescription(), kw)
                || containsIgnoreCase(t.getCategory(), kw)
                || (t.getSystemPrompt() != null && containsIgnoreCase(t.getSystemPrompt(), kw)))
            .collect(Collectors.toList());
    }

    @Override
    public List<AgentTemplate> mostUsed(int limit) {
        return templates.values().stream()
            .sorted(Comparator.comparingLong(AgentTemplate::getUsageCount).reversed())
            .limit(Math.max(0, limit))
            .collect(Collectors.toList());
    }

    @Override
    public void recordUsage(String templateId) {
        AgentTemplate t = templates.get(templateId);
        if (t != null) t.setUsageCount(t.getUsageCount() + 1);
    }

    @Override
    public AgentTemplate register(AgentTemplate template) {
        if (template == null || template.getId() == null || template.getId().isBlank()) {
            throw new IllegalArgumentException("模板 ID 必填");
        }
        templates.put(template.getId(), template);
        log.info("[AgentTemplate] 注册模板: {} ({})", template.getId(), template.getName());
        return template;
    }

    @Override
    public boolean remove(String id) {
        AgentTemplate removed = templates.remove(id);
        return removed != null;
    }

    @Override
    public List<String> categories() {
        return templates.values().stream()
            .map(AgentTemplate::getCategory)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    private boolean containsIgnoreCase(String text, String kw) {
        return text != null && text.toLowerCase().contains(kw);
    }
}
