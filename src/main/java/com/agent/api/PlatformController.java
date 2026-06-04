package com.agent.api;

import com.agent.dto.AgentMetricsSummary;
import com.agent.entity.AgentMetricsRecord;
import com.agent.entity.AgentTemplate;
import com.agent.service.AgentMetricsService;
import com.agent.service.AgentTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 平台 REST API - 模板、指标、工具管理
 *
 * 提供 Agent 平台的后台管理能力：模板浏览/搜索、调用指标查询、工具列表等。
 */
@Slf4j
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformController {

    private final AgentTemplateService templateService;
    private final AgentMetricsService metricsService;

    // ============== Agent 模板 ==============

    /**
     * 列出所有模板
     */
    @GetMapping("/templates")
    public List<AgentTemplate> listTemplates(
            @RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) {
            return templateService.listByCategory(category);
        }
        return templateService.listAll();
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/templates/{id}")
    public Map<String, Object> getTemplate(@PathVariable String id) {
        Optional<AgentTemplate> t = templateService.getById(id);
        if (t.isEmpty()) {
            return Map.of("success", false, "error", "模板不存在: " + id);
        }
        return Map.of("success", true, "template", t.get());
    }

    /**
     * 搜索模板
     */
    @GetMapping("/templates/search")
    public List<AgentTemplate> searchTemplates(@RequestParam String q) {
        return templateService.search(q);
    }

    /**
     * 获取最常用模板
     */
    @GetMapping("/templates/top")
    public List<AgentTemplate> topTemplates(@RequestParam(defaultValue = "5") int limit) {
        return templateService.mostUsed(limit);
    }

    /**
     * 列出所有分类
     */
    @GetMapping("/templates/categories")
    public List<String> categories() {
        return templateService.categories();
    }

    /**
     * 注册自定义模板
     */
    @PostMapping("/templates")
    public Map<String, Object> registerTemplate(@RequestBody AgentTemplate template) {
        try {
            AgentTemplate saved = templateService.register(template);
            return Map.of("success", true, "template", saved);
        } catch (Exception e) {
            log.error("注册模板失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 记录模板使用
     */
    @PostMapping("/templates/{id}/use")
    public Map<String, Object> useTemplate(@PathVariable String id) {
        if (templateService.getById(id).isEmpty()) {
            return Map.of("success", false, "error", "模板不存在: " + id);
        }
        templateService.recordUsage(id);
        return Map.of("success", true, "id", id);
    }

    // ============== 调用指标 ==============

    /**
     * 记录一次 Agent 调用（外部系统调用）
     */
    @PostMapping("/metrics")
    public Map<String, Object> recordMetric(@RequestBody AgentMetricsRecord record) {
        try {
            metricsService.record(record);
            return Map.of("success", true, "id", record.getId());
        } catch (Exception e) {
            log.error("记录指标失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取汇总（最近 N 小时）
     */
    @GetMapping("/metrics/summary")
    public AgentMetricsSummary summary(
            @RequestParam(required = false) Integer hours) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = hours != null ? to.minusHours(hours) : null;
        return metricsService.summarize(from, to);
    }

    /**
     * 时间区间汇总
     */
    @GetMapping("/metrics/summary/range")
    public AgentMetricsSummary summaryRange(
            @RequestParam String from,
            @RequestParam String to) {
        return metricsService.summarize(LocalDateTime.parse(from), LocalDateTime.parse(to));
    }

    /**
     * 最近 N 条记录
     */
    @GetMapping("/metrics/recent")
    public List<AgentMetricsRecord> recent(@RequestParam(defaultValue = "20") int limit) {
        return metricsService.recent(limit);
    }

    /**
     * 用户的最近记录
     */
    @GetMapping("/metrics/recent/user")
    public List<AgentMetricsRecord> recentByUser(
            @RequestParam String userId,
            @RequestParam(defaultValue = "20") int limit) {
        return metricsService.recentByUser(userId, limit);
    }

    /**
     * 最近失败记录
     */
    @GetMapping("/metrics/failures")
    public List<AgentMetricsRecord> failures(@RequestParam(defaultValue = "20") int limit) {
        return metricsService.topFailures(limit);
    }

    /**
     * 总调用次数
     */
    @GetMapping("/metrics/count")
    public Map<String, Object> count() {
        return Map.of("total", metricsService.countAll());
    }
}
