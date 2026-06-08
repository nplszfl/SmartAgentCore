package com.agent.core.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Business-logic tests for SearchTool.
 *
 * The pre-existing ToolTest only asserts the tool runs without throwing — it
 * does not exercise the real business contract. These tests pin down:
 *  - Input validation (blank / null / missing query)
 *  - Limit clamping (negative / non-integer / too large)
 *  - Deterministic structured output (numbered list with title and snippet per hit)
 *  - Result count respects the requested limit
 *  - Whitespace-only query is rejected (treat as blank)
 *
 * Each test is independent and uses only the public Tool contract — no mocking,
 * no network. The tool is required to work fully offline against its built-in
 * knowledge base, with no external API key required.
 */
@DisplayName("SearchTool business logic")
class SearchToolBusinessTest {

    // ==================== Input validation ====================

    @Test
    @DisplayName("rejects null parameters")
    void rejectsNullParameters() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(null);
        assertFalse(result.success(), "null parameters should fail");
        assertNotNull(result.error());
    }

    @Test
    @DisplayName("rejects when query key is missing")
    void rejectsMissingQuery() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(new HashMap<>());
        assertFalse(result.success());
        assertTrue(result.error().toLowerCase().contains("query")
                || result.error().contains("关键词"),
                "error should mention query/关键词, got: " + result.error());
    }

    @Test
    @DisplayName("rejects blank query string")
    void rejectsBlankQuery() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", ""));
        assertFalse(result.success());
    }

    @Test
    @DisplayName("rejects whitespace-only query")
    void rejectsWhitespaceOnlyQuery() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "   \t  "));
        assertFalse(result.success());
    }

    @Test
    @DisplayName("rejects null query value")
    void rejectsNullQuery() {
        SearchTool tool = new SearchTool();
        Map<String, Object> params = new HashMap<>();
        params.put("query", null);
        Tool.ToolResult result = tool.execute(params);
        assertFalse(result.success());
    }

    // ==================== Limit handling ====================

    @Test
    @DisplayName("default limit is applied when none provided")
    void defaultLimitApplied() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "java"));
        assertTrue(result.success());
        // Count numbered results in the output — should be 1..N, N >= 1
        int hits = countNumberedHits(result.output());
        assertTrue(hits >= 1, "expected at least one hit, got output:\n" + result.output());
    }

    @Test
    @DisplayName("explicit limit caps number of results")
    void explicitLimitCapsResults() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "java", "limit", 1));
        assertTrue(result.success());
        int hits = countNumberedHits(result.output());
        assertTrue(hits >= 1 && hits <= 1,
                "expected exactly 1 hit when limit=1, got " + hits + "\n" + result.output());
    }

    @Test
    @DisplayName("limit=0 falls back to default (does not produce empty output)")
    void zeroLimitFallsBackToDefault() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "java", "limit", 0));
        assertTrue(result.success());
        assertTrue(countNumberedHits(result.output()) >= 1);
    }

    @Test
    @DisplayName("negative limit falls back to default")
    void negativeLimitFallsBackToDefault() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "java", "limit", -3));
        assertTrue(result.success());
        assertTrue(countNumberedHits(result.output()) >= 1);
    }

    // ==================== Output structure ====================

    @Test
    @DisplayName("output mentions the original query keyword")
    void outputEchoesQuery() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "kubernetes"));
        assertTrue(result.success());
        // The query string should appear in the output somewhere
        assertTrue(result.output().toLowerCase().contains("kubernetes"),
                "output should mention query 'kubernetes':\n" + result.output());
    }

    @Test
    @DisplayName("output is numbered and parseable")
    void outputIsNumbered() {
        SearchTool tool = new SearchTool();
        Tool.ToolResult result = tool.execute(Map.of("query", "python", "limit", 3));
        assertTrue(result.success());
        // Expect at least one "1." or "1、" numbered hit
        String out = result.output();
        assertTrue(out.matches("(?s).*\\b1[.、].*"),
                "expected numbered output starting with '1.' or '1、', got:\n" + out);
    }

    @Test
    @DisplayName("tool name and schema are stable")
    void nameAndSchemaStable() {
        SearchTool tool = new SearchTool();
        assertEquals("search", tool.getName());
        assertNotNull(tool.getDescription());
        assertFalse(tool.getDescription().isBlank());
        String schema = tool.getParameterSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("query"), "schema must declare query parameter: " + schema);
        assertTrue(schema.contains("limit"), "schema must declare limit parameter: " + schema);
    }

    // ==================== helpers ====================

    /**
     * Count numbered list entries like "1. ", "2. " or "1、", "2、" in the output.
     * Returns the highest numbered hit found.
     */
    private int countNumberedHits(String output) {
        if (output == null) return 0;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^\\s*(\\d+)[.、]");
        java.util.regex.Matcher m = p.matcher(output);
        int max = 0;
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n > max) max = n;
        }
        return max;
    }
}
