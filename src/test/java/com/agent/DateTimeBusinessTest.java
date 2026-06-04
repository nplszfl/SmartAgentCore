package com.agent;

import com.agent.core.tool.DateTimeTool;
import com.agent.core.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateTimeTool 业务功能测试 - 覆盖全部日期操作
 */
class DateTimeBusinessTest {

    private final DateTimeTool tool = new DateTimeTool();

    @Nested
    @DisplayName("now 操作")
    class NowOperation {
        @Test void defaultNow() {
            Tool.ToolResult r = tool.execute(Map.of("operation", "now"));
            assertTrue(r.success());
            assertTrue(r.output().contains("formatted="));
            assertTrue(r.output().contains("timezone=Asia/Shanghai"));
        }
        @Test void customTimezone() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "now",
                "timezone", "America/New_York",
                "format", "iso"));
            assertTrue(r.success());
            assertTrue(r.output().contains("America/New_York"));
        }
        @Test void customFormat() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "now",
                "format", "yyyyMMdd"));
            assertTrue(r.success());
            // 2025 or 2026 - 8 digits
            assertTrue(r.output().matches("(?s).*formatted=\\d{8}.*"));
        }
    }

    @Nested
    @DisplayName("add 操作")
    class AddOperation {
        @Test void addDays() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "add",
                "from_date", "2025-01-01",
                "amount", 7,
                "unit", "days"));
            assertTrue(r.success());
            assertEquals("2025-01-08 00:00:00", r.output());
        }
        @Test void addMonths() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "add",
                "from_date", "2025-01-15 10:30:00",
                "amount", 3,
                "unit", "months"));
            assertTrue(r.success());
            assertEquals("2025-04-15 10:30:00", r.output());
        }
        @Test void addHours() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "add",
                "from_date", "2025-01-01 00:00:00",
                "amount", 25,
                "unit", "hours"));
            assertTrue(r.success());
            assertEquals("2025-01-02 01:00:00", r.output());
        }
    }

    @Nested
    @DisplayName("diff 操作")
    class DiffOperation {
        @Test void diffDays() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "diff",
                "from_date", "2025-01-01",
                "to_date", "2025-01-10"));
            assertTrue(r.success());
            assertTrue(r.output().contains("days=9"));
        }
    }

    @Nested
    @DisplayName("business_days 操作")
    class BusinessDays {
        @Test void fullWeek() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "business_days",
                "from_date", "2025-01-06",  // 周一
                "to_date", "2025-01-12"));   // 周日
            assertTrue(r.success());
            assertEquals("5", r.output());
        }
        @Test void onlyWeekend() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "business_days",
                "from_date", "2025-01-04",  // 周六
                "to_date", "2025-01-05"));   // 周日
            assertTrue(r.success());
            assertEquals("0", r.output());
        }
    }

    @Nested
    @DisplayName("weekday 操作")
    class Weekday {
        @Test void monday() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "weekday", "date", "2025-01-06"));
            assertTrue(r.success());
            assertTrue(r.output().contains("星期一"));
        }
        @Test void saturday() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "weekday", "date", "2025-01-04"));
            assertTrue(r.success());
            assertTrue(r.output().contains("星期六"));
        }
    }

    @Nested
    @DisplayName("next_business_day 操作")
    class NextBusinessDay {
        @Test void fromFriday() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "next_business_day", "date", "2025-01-03")); // 周五
            assertTrue(r.success());
            assertEquals("2025-01-06", r.output());
        }
        @Test void fromSunday() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "next_business_day", "date", "2025-01-05")); // 周日
            assertTrue(r.success());
            assertEquals("2025-01-06", r.output());
        }
        @Test void fromWednesday() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "next_business_day", "date", "2025-01-08")); // 周三
            assertTrue(r.success());
            assertEquals("2025-01-09", r.output());
        }
    }

    @Nested
    @DisplayName("convert 时区")
    class TimezoneConvert {
        @Test void shToNY() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "convert",
                "date", "2025-01-01 12:00:00",
                "source_timezone", "Asia/Shanghai",
                "target_timezone", "America/New_York"));
            assertTrue(r.success());
            // 上海 12 点 = 纽约前一天 23 点（冬令时）
            assertTrue(r.output().contains("2024-12-31T23:00:00"));
        }
    }

    @Nested
    @DisplayName("parse 操作")
    class ParseOperation {
        @Test void parseDate() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "parse",
                "date", "2025-01-15"));
            assertTrue(r.success());
            assertTrue(r.output().contains("date=2025-01-15"));
        }
    }
}
