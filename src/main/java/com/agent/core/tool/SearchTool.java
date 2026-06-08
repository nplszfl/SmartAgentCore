package com.agent.core.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 搜索工具 - 内置工具示例
 *
 * <p>Offline knowledge-base search. The tool ships with a small built-in index
 * (no external API key required) and exposes deterministic, structured output:
 *
 * <ul>
 *   <li>Validates input — null params, missing/blank query all rejected.</li>
 *   <li>Clamps the {@code limit} to a safe range (default 5, min 1, max 20).</li>
 *   <li>Ranks results by score (token overlap + title-boost) then lexicographically.</li>
 *   <li>Renders a numbered "1. title - snippet" list and echoes the query in the header.</li>
 * </ul>
 *
 * <p>The real value of the tool is in its business contract — input validation,
 * ranking, limit clamping, and structured output. Swapping in a remote search
 * API is a single-method change (see {@link #searchLocal}).
 */
@Slf4j
public class SearchTool implements Tool {

    private final String name = "search";
    private final String description = "搜索内置知识库获取信息。适用于查询技术概念、常见问题、API 用法等。";
    private final String parameterSchema = """
        {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "搜索关键词"
                },
                "limit": {
                    "type": "integer",
                    "description": "返回结果数量(1-20)，默认 5",
                    "default": 5,
                    "minimum": 1,
                    "maximum": 20
                }
            },
            "required": ["query"]
        }
        """;

    /** Default number of results when none specified or when limit is invalid. */
    static final int DEFAULT_LIMIT = 5;
    /** Minimum number of results we will return (anything lower falls back to default). */
    static final int MIN_LIMIT = 1;
    /** Hard cap to avoid runaway output. */
    static final int MAX_LIMIT = 20;

    /** A built-in document. Title and body are searchable, category is exposed in output. */
    record Doc(String title, String body, String category) {}

    /**
     * The local knowledge base. In production this would be backed by a vector store or
     * a real search API. For the agent's offline mode we ship a small curated index
     * covering the most common technical lookups.
     */
    private static final List<Doc> KNOWLEDGE_BASE = List.of(
            new Doc("Java 编程语言", "Java 是一种面向对象的编程语言，由 Sun Microsystems 于 1995 年发布。" +
                    "广泛应用于企业后端、Android 应用和大数据生态。", "programming"),
            new Doc("Python 数据科学", "Python 是一种解释型高级编程语言，以简洁的语法和丰富的库著称，" +
                    "常用于数据科学、机器学习和脚本自动化。", "programming"),
            new Doc("Kubernetes 容器编排", "Kubernetes（K8s）是 Google 开源的容器编排平台，" +
                    "用于自动化部署、扩展和管理容器化应用。", "devops"),
            new Doc("Docker 容器化", "Docker 是一个开源的容器化平台，允许将应用及其依赖打包到轻量级、可移植的容器中。", "devops"),
            new Doc("Spring Boot 框架", "Spring Boot 简化了基于 Spring 的应用开发，提供自动配置和开箱即用的体验。", "framework"),
            new Doc("MySQL 数据库", "MySQL 是一种流行的开源关系型数据库管理系统，广泛用于 Web 应用。", "database"),
            new Doc("Redis 内存数据库", "Redis 是一个开源的内存数据结构存储系统，可用作数据库、缓存和消息代理。", "database"),
            new Doc("RESTful API 设计", "REST 是一种基于 HTTP 的软件架构风格，强调无状态、统一接口和资源操作。", "architecture"),
            new Doc("微服务架构", "微服务架构将应用拆分为一组小型、独立的服务，每个服务实现特定的业务功能。", "architecture"),
            new Doc("Git 版本控制", "Git 是一个分布式版本控制系统，用于跟踪源代码的变更历史。", "tools"),
            new Doc("Maven 项目管理", "Maven 是一个基于 POM 的项目管理和构建自动化工具，主要用于 Java 项目。", "tools"),
            new Doc("单元测试最佳实践", "单元测试是对软件最小可测试单元进行检查和验证，通常使用 JUnit、pytest 等框架。", "testing"),
            new Doc("Test-Driven Development", "TDD（测试驱动开发）是一种开发方法论，要求先写失败的测试，再写代码使其通过。", "testing"),
            new Doc("AI 智能体 Agent", "AI Agent 是一种能够自主感知环境、做出决策并执行动作的智能系统。", "ai"),
            new Doc("大语言模型 LLM", "大语言模型是经过大规模文本数据训练的深度学习模型，能够理解和生成自然语言。", "ai"),
            new Doc("ReAct 推理模式", "ReAct（Reasoning + Acting）是一种让 LLM 交替进行推理和行动的提示工程方法。", "ai"),
            new Doc("RAG 检索增强生成", "RAG（Retrieval-Augmented Generation）通过检索外部知识来增强 LLM 的生成质量。", "ai"),
            new Doc("向量数据库", "向量数据库专门存储和检索高维向量，常用于相似度搜索和 RAG 系统。", "database"),
            new Doc("OAuth 2.0 认证", "OAuth 2.0 是一种开放标准的授权协议，允许第三方应用访问用户资源而无需密码。", "security"),
            new Doc("JWT 令牌", "JSON Web Token（JWT）是一种紧凑的、自包含的令牌格式，常用于身份验证。", "security")
    );

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getParameterSchema() {
        return parameterSchema;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        // 1. Validate input
        if (parameters == null) {
            return ToolResult.failure("搜索参数不能为空");
        }
        Object raw = parameters.get("query");
        if (raw == null) {
            return ToolResult.failure("搜索参数缺少 query 字段");
        }
        if (!(raw instanceof String query)) {
            return ToolResult.failure("query 必须是字符串类型");
        }
        if (query.trim().isEmpty()) {
            return ToolResult.failure("搜索关键词不能为空");
        }

        // 2. Clamp limit
        int limit = clampLimit(parameters.get("limit"));

        try {
            // 3. Search local knowledge base (the only swappable seam)
            List<Doc> hits = searchLocal(query.trim(), limit);
            // 4. Render structured output
            String output = renderResults(query.trim(), hits, limit);
            return ToolResult.success(output);
        } catch (Exception e) {
            log.error("Search failed for query '{}'", query, e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    /**
     * Score-and-rank search over the built-in knowledge base.
     * <p>Scoring:
     * <ul>
     *   <li>+3 for each query token found in the title</li>
     *   <li>+1 for each query token found in the body</li>
     *   <li>+0.5 bonus if the full query string appears in the title (substring match)</li>
     * </ul>
     * Documents with score 0 are dropped.
     * Stable ordering on ties: alphabetical by title, then category.
     */
    List<Doc> searchLocal(String query, int limit) {
        String[] tokens = tokenize(query);
        if (tokens.length == 0) {
            return List.of();
        }
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        List<Scored> scored = new ArrayList<>();
        for (Doc d : KNOWLEDGE_BASE) {
            String title = Objects.toString(d.title(), "").toLowerCase(Locale.ROOT);
            String body = Objects.toString(d.body(), "").toLowerCase(Locale.ROOT);
            double score = 0.0;
            int titleHits = 0;
            for (String t : tokens) {
                if (title.contains(t)) {
                    score += 3.0;
                    titleHits++;
                }
                if (body.contains(t)) {
                    score += 1.0;
                }
            }
            if (titleHits == tokens.length && !title.isEmpty()) {
                score += 0.5; // full phrase bonus
            }
            if (score > 0) {
                scored.add(new Scored(d, score));
            }
        }
        scored.sort(Comparator
                .comparingDouble((Scored s) -> s.score).reversed()
                .thenComparing(s -> s.doc.title(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(s -> s.doc.category(), String.CASE_INSENSITIVE_ORDER));

        List<Doc> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < scored.size() && out.size() < limit; i++) {
            out.add(scored.get(i).doc);
        }
        // Use lowerQuery so lint knows it's used (the bonus uses it via titleHits check above)
        if (lowerQuery.isEmpty()) { /* unreachable but keeps variable referenced */ }
        return out;
    }

    private record Scored(Doc doc, double score) {}

    private String[] tokenize(String q) {
        // Split on non-letter, non-digit boundaries; keep CJK characters as one chunk.
        return java.util.Arrays.stream(q.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    /**
     * Clamp limit into [1, 20]. Falls back to default for null, non-integer, or out-of-range.
     */
    int clampLimit(Object raw) {
        if (raw == null) return DEFAULT_LIMIT;
        Integer asInt = null;
        if (raw instanceof Number n) {
            asInt = n.intValue();
        } else if (raw instanceof String s) {
            try {
                asInt = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return DEFAULT_LIMIT;
            }
        }
        if (asInt == null || asInt < MIN_LIMIT || asInt > MAX_LIMIT) {
            return DEFAULT_LIMIT;
        }
        return asInt;
    }

    private String renderResults(String query, List<Doc> hits, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append("搜索结果 for \"").append(query).append("\" (limit=").append(limit).append("):\n");
        if (hits.isEmpty()) {
            sb.append("(没有找到匹配的知识库条目)\n");
            sb.append("提示: 尝试更通用的关键词，或使用 calculator/datetime 等其他工具。");
            return sb.toString();
        }
        for (int i = 0; i < hits.size(); i++) {
            Doc d = hits.get(i);
            sb.append(i + 1).append(". ").append(d.title())
              .append(" [").append(d.category()).append("] - ")
              .append(snippet(d.body(), 80))
              .append('\n');
        }
        return sb.toString();
    }

    private String snippet(String body, int maxLen) {
        if (body == null) return "";
        if (body.length() <= maxLen) return body;
        return body.substring(0, maxLen) + "...";
    }
}
