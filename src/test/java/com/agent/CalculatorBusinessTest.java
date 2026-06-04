package com.agent;

import com.agent.core.tool.CalculatorTool;
import com.agent.core.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CalculatorTool 业务功能测试 - 覆盖所有运算场景
 */
class CalculatorBusinessTest {

    private final CalculatorTool tool = new CalculatorTool();

    @Nested
    @DisplayName("基础四则运算")
    class BasicArithmetic {
        @Test void addition() {
            assertEquals("4", runExpr("2+2"));
        }
        @Test void subtraction() {
            assertEquals("1", runExpr("3-2"));
        }
        @Test void multiplication() {
            assertEquals("12", runExpr("3*4"));
        }
        @Test void division() {
            assertEquals("5", runExpr("10/2"));
        }
        @Test void precedence() {
            // 3 + 4 * 2 = 11
            assertEquals("11", runExpr("3+4*2"));
        }
        @Test void parentheses() {
            // (3+4) * 2 = 14
            assertEquals("14", runExpr("(3+4)*2"));
        }
        @Test void nestedParens() {
            // ((2+3)*4)-5 = 15
            assertEquals("15", runExpr("((2+3)*4)-5"));
        }
        @Test void unaryMinus() {
            assertEquals("-3", runExpr("-3"));
        }
        @Test void negativeArithmetic() {
            assertEquals("2", runExpr("5+-3"));
        }
    }

    @Nested
    @DisplayName("数学函数")
    class MathFunctions {
        @Test void sqrt() { assertEquals("4", runExpr("sqrt(16)")); }
        @Test void abs()  { assertEquals("5", runExpr("abs(-5)")); }
        @Test void sin()  { assertTrue(runExpr("sin(0)").equals("0")); }
        @Test void cos()  { assertTrue(runExpr("cos(0)").equals("1")); }
        @Test void power() { assertEquals("8", runExpr("2^3")); }
        @Test void factorial() {
            assertEquals("120", runExpr("5!"));
        }
    }

    @Nested
    @DisplayName("统计运算")
    class Statistics {
        @Test void mean() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "statistics",
                "numbers", List.of(1.0, 2.0, 3.0, 4.0, 5.0)
            ));
            assertTrue(r.success());
            assertTrue(r.output().contains("mean=3"));
            assertTrue(r.output().contains("sum=15"));
            assertTrue(r.output().contains("max=5"));
            assertTrue(r.output().contains("min=1"));
            assertTrue(r.output().contains("median=3"));
        }
        @Test void rejectsEmptyList() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "statistics",
                "numbers", List.of()
            ));
            assertFalse(r.success());
        }
    }

    @Nested
    @DisplayName("单位换算")
    class UnitConversion {
        @Test void lengthKmToM() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "convert", "value", 1.0,
                "from_unit", "km", "to_unit", "m", "category", "length"));
            assertTrue(r.success());
            assertEquals("1000 m", r.output());
        }
        @Test void temperatureCtoF() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "convert", "value", 100.0,
                "from_unit", "C", "to_unit", "F", "category", "temperature"));
            assertTrue(r.success());
            assertEquals("212 F", r.output());
        }
        @Test void weightKgToLb() {
            Tool.ToolResult r = tool.execute(Map.of(
                "operation", "convert", "value", 1.0,
                "from_unit", "kg", "to_unit", "lb", "category", "weight"));
            assertTrue(r.success());
            assertTrue(r.output().startsWith("2.20"));
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandling {
        @Test void divisionByZero() {
            Tool.ToolResult r = tool.execute(Map.of("expression", "1/0"));
            assertFalse(r.success());
        }
        @Test void emptyExpression() {
            Tool.ToolResult r = tool.execute(Map.of("expression", ""));
            assertFalse(r.success());
        }
        @Test void invalidExpression() {
            Tool.ToolResult r = tool.execute(Map.of("expression", "abc"));
            assertFalse(r.success());
        }
        @Test void unknownOperation() {
            Tool.ToolResult r = tool.execute(Map.of("operation", "unknown"));
            assertFalse(r.success());
        }
        @Test void factorialOverflow() {
            Tool.ToolResult r = tool.execute(Map.of("expression", "25!"));
            assertFalse(r.success());
        }
    }

    private String runExpr(String expr) {
        Tool.ToolResult r = tool.execute(Map.of("expression", expr));
        assertTrue(r.success(), "Expected success for " + expr + " but got error: " + r.error());
        return r.output();
    }
}
