package com.agent.core.tool;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 计算器工具 - 内置工具示例
 */
public class CalculatorTool implements Tool {

    private final String name = "calculator";
    private final String description = "执行数学计算。支持加减乘除、幂运算、三角函数等基本数学运算。";
    private final String parameterSchema = """
        {
            "type": "object",
            "properties": {
                "expression": {
                    "type": "string",
                    "description": "数学表达式，如: 2+2, sin(3.14), sqrt(16), 10^2"
                }
            },
            "required": ["expression"]
        }
        """;

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
        try {
            String expression = (String) parameters.get("expression");
            if (expression == null || expression.isBlank()) {
                return ToolResult.failure("表达式不能为空");
            }

            double result = evaluateExpression(expression);
            return ToolResult.success(String.valueOf(result));
        } catch (Exception e) {
            return ToolResult.failure("计算错误: " + e.getMessage());
        }
    }

    private double evaluateExpression(String expr) throws Exception {
        expr = expr.replace(" ", "").replaceAll("(?<=[0-9])-", "+-");

        // 处理特殊函数
        if (expr.startsWith("sin(") && expr.endsWith(")")) {
            double value = Double.parseDouble(expr.substring(4, expr.length() - 1));
            return Math.sin(value);
        }
        if (expr.startsWith("cos(") && expr.endsWith(")")) {
            double value = Double.parseDouble(expr.substring(4, expr.length() - 1));
            return Math.cos(value);
        }
        if (expr.startsWith("sqrt(") && expr.endsWith(")")) {
            double value = Double.parseDouble(expr.substring(5, expr.length() - 1));
            return Math.sqrt(value);
        }
        if (expr.startsWith("log(") && expr.endsWith(")")) {
            double value = Double.parseDouble(expr.substring(4, expr.length() - 1));
            return Math.log(value);
        }
        if (expr.startsWith("abs(") && expr.endsWith(")")) {
            double value = Double.parseDouble(expr.substring(4, expr.length() - 1));
            return Math.abs(value);
        }

        // 处理幂运算
        if (expr.contains("^")) {
            String[] parts = expr.split("\\^", 2);
            return Math.pow(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
        }

        // 简单四则运算
        return evaluateBasicMath(expr);
    }

    private double evaluateBasicMath(String expr) throws Exception {
        // 移除所有空格
        expr = expr.replace(" ", "");
        
        // 处理加减法（注意：已经将减法转换为加负数）
        if (expr.contains("+")) {
            String[] parts = expr.split("\\+", -1);
            double sum = 0;
            for (String part : parts) {
                if (!part.isEmpty()) {
                    sum += evaluateMulDiv(part);
                }
            }
            return sum;
        }
        
        return evaluateMulDiv(expr);
    }

    private double evaluateMulDiv(String expr) throws Exception {
        // 处理乘除法，从左到右
        Pattern pattern = Pattern.compile("^([+-]?[0-9.]+)([*/])(.+)");
        while (true) {
            var matcher = pattern.matcher(expr);
            if (!matcher.find()) {
                break;
            }
            double left = Double.parseDouble(matcher.group(1));
            String op = matcher.group(2);
            String rightPart = matcher.group(3);
            
            // 找到下一个数字
            Pattern numPattern = Pattern.compile("^([+-]?[0-9.]+)(.*)");
            var numMatcher = numPattern.matcher(rightPart);
            if (!numMatcher.find()) {
                throw new Exception("无效表达式");
            }
            double right = Double.parseDouble(numMatcher.group(1));
            
            double result = op.equals("*") ? left * right : left / right;
            expr = expr.substring(0, matcher.start()) + result + numMatcher.group(2);
        }
        
        return Double.parseDouble(expr);
    }
}
