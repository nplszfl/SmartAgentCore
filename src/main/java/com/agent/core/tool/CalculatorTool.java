package com.agent.core.tool;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 计算器工具 - 增强版，支持四则运算、三角函数、对数、阶乘、统计、百分比、单位换算等。
 *
 * 支持的操作 (operation 参数):
 *   - evaluate   表达式求值（默认行为）
 *   - statistics 统计数据（mean/min/max/sum/median/stdev）
 *   - percentage 百分比运算（percent / increase / discount）
 *   - convert    单位换算（长度/重量/温度/存储/时间）
 */
public class CalculatorTool implements Tool {

    private final String name = "calculator";
    private final String description = "执行数学计算。支持加减乘除、幂运算、三角函数、阶乘、统计、百分比、单位换算等。";
    private final String parameterSchema = """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["evaluate", "statistics", "percentage", "convert"],
                    "description": "操作类型，默认 evaluate"
                },
                "expression": {
                    "type": "string",
                    "description": "数学表达式 (e.g. 2+3*4, sin(3.14), sqrt(16), 5!)"
                },
                "numbers": {
                    "type": "array",
                    "items": {"type": "number"},
                    "description": "数字数组 (statistics 操作需要)"
                },
                "value": {
                    "type": "number",
                    "description": "原始值 (percentage / convert 操作需要)"
                },
                "base": {
                    "type": "number",
                    "description": "基准值 (percentage 操作中 percent_of / increase 时使用)"
                },
                "from_unit": {
                    "type": "string",
                    "description": "源单位 (convert 操作需要)"
                },
                "to_unit": {
                    "type": "string",
                    "description": "目标单位 (convert 操作需要)"
                },
                "category": {
                    "type": "string",
                    "enum": ["length", "weight", "temperature", "storage", "time"],
                    "description": "换算类别 (convert 操作需要)"
                }
            }
        }
        """;

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public String getParameterSchema() { return parameterSchema; }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        try {
            String operation = parameters.containsKey("operation")
                ? parameters.get("operation").toString()
                : "evaluate";
            switch (operation) {
                case "evaluate":   return doEvaluate(parameters);
                case "statistics": return doStatistics(parameters);
                case "percentage": return doPercentage(parameters);
                case "convert":    return doConvert(parameters);
                default: return ToolResult.failure("未知操作: " + operation);
            }
        } catch (Exception e) {
            return ToolResult.failure("计算错误: " + e.getMessage());
        }
    }

    // ============== operations ==============

    @SuppressWarnings("unchecked")
    private ToolResult doEvaluate(Map<String, Object> p) {
        String expression = p.containsKey("expression") ? p.get("expression").toString() : null;
        if (expression == null || expression.isBlank()) {
            return ToolResult.failure("expression 不能为空");
        }
        double result = evaluateExpression(expression);
        return ToolResult.success(formatNumber(result));
    }

    @SuppressWarnings("unchecked")
    private ToolResult doStatistics(Map<String, Object> p) {
        Object numbersObj = p.get("numbers");
        if (!(numbersObj instanceof java.util.List)) {
            return ToolResult.failure("numbers 必须是数组");
        }
        java.util.List<Object> list = (java.util.List<Object>) numbersObj;
        if (list.isEmpty()) {
            return ToolResult.failure("numbers 不能为空");
        }
        double[] values = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            values[i] = toDouble(list.get(i));
        }

        double sum = 0; double min = values[0]; double max = values[0];
        for (double v : values) { sum += v; if (v < min) min = v; if (v > max) max = v; }
        double mean = sum / values.length;

        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        double median = sorted.length % 2 == 1
            ? sorted[sorted.length / 2]
            : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2.0;

        double variance = 0;
        for (double v : values) variance += (v - mean) * (v - mean);
        variance /= values.length;
        double stdev = Math.sqrt(variance);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", values.length);
        result.put("sum", formatNumber(sum));
        result.put("mean", formatNumber(mean));
        result.put("median", formatNumber(median));
        result.put("min", formatNumber(min));
        result.put("max", formatNumber(max));
        result.put("stdev", formatNumber(stdev));
        result.put("range", formatNumber(max - min));
        return ToolResult.success(formatMap(result));
    }

    private ToolResult doPercentage(Map<String, Object> p) {
        Object valueObj = p.get("value");
        Object baseObj = p.get("base");
        if (valueObj == null) {
            return ToolResult.failure("value 必填");
        }
        double value = toDouble(valueObj);

        // value 自身是百分比，求对应的实际值
        if (baseObj != null) {
            double base = toDouble(baseObj);
            double percentValue = base * value / 100.0;
            double increaseTo = base + percentValue;
            double decreaseTo = base - percentValue;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("value_of_percent", formatNumber(percentValue));
            result.put("base_plus", formatNumber(increaseTo));
            result.put("base_minus", formatNumber(decreaseTo));
            result.put("change_ratio", formatNumber(value));
            return ToolResult.success(formatMap(result));
        }

        // 计算 value / 100 表达式
        return ToolResult.success(formatNumber(value));
    }

    private ToolResult doConvert(Map<String, Object> p) {
        Object valueObj = p.get("value");
        String fromUnit = getString(p, "from_unit", null);
        String toUnit   = getString(p, "to_unit", null);
        String category = getString(p, "category", null);
        if (valueObj == null || fromUnit == null || toUnit == null || category == null) {
            return ToolResult.failure("value / from_unit / to_unit / category 必填");
        }
        double value = toDouble(valueObj);
        Double converted = convertUnits(value, category, fromUnit, toUnit);
        if (converted == null) {
            return ToolResult.failure("不支持的换算: " + category + " " + fromUnit + " -> " + toUnit);
        }
        return ToolResult.success(formatNumber(converted) + " " + toUnit);
    }

    // ============== expression engine ==============

    private double evaluateExpression(String expr) {
        String original = expr.trim();
        if (original.endsWith("!")) {
            int n = Integer.parseInt(original.substring(0, original.length() - 1).trim());
            return factorial(n);
        }

        // 三角函数
        if (original.startsWith("sin(") && original.endsWith(")")) {
            return Math.sin(Double.parseDouble(extract(original, 4)));
        }
        if (original.startsWith("cos(") && original.endsWith(")")) {
            return Math.cos(Double.parseDouble(extract(original, 4)));
        }
        if (original.startsWith("tan(") && original.endsWith(")")) {
            return Math.tan(Double.parseDouble(extract(original, 4)));
        }
        if (original.startsWith("sqrt(") && original.endsWith(")")) {
            return Math.sqrt(Double.parseDouble(extract(original, 5)));
        }
        if (original.startsWith("log(") && original.endsWith(")")) {
            return Math.log10(Double.parseDouble(extract(original, 4)));
        }
        if (original.startsWith("ln(") && original.endsWith(")")) {
            return Math.log(Double.parseDouble(extract(original, 3)));
        }
        if (original.startsWith("abs(") && original.endsWith(")")) {
            return Math.abs(Double.parseDouble(extract(original, 4)));
        }

        // 幂运算
        if (original.contains("^")) {
            int idx = original.indexOf("^");
            double left = evaluateExpression(original.substring(0, idx));
            double right = evaluateExpression(original.substring(idx + 1));
            return Math.pow(left, right);
        }

        return evaluateBasicMath(original.replace(" ", ""));
    }

    private double evaluateBasicMath(String expr) {
        // 处理括号
        while (expr.contains("(")) {
            int open = expr.lastIndexOf("(");
            int close = expr.indexOf(")", open);
            if (close < 0) throw new IllegalArgumentException("括号不匹配");
            double sub = evaluateExpression(expr.substring(open + 1, close));
            expr = expr.substring(0, open) + formatNumber(sub) + expr.substring(close + 1);
        }

        // 两阶段处理：先 * / ，再 + -
        // 第一阶段：合并相邻的乘除
        String stage1 = collapseAdjacent(expr, "*", "/");
        // 第二阶段：合并相邻的加减
        String stage2 = collapseAdjacent(stage1, "+", "-");
        return Double.parseDouble(stage2);
    }

    /**
     * 把 expr 中由 op1/op2 分隔的相邻运算折叠成单个结果（保持左结合）。
     * 例：collapseAdjacent("3*4*2", "*", "/") = "24"
     *     collapseAdjacent("3+4+2", "+", "-") = "9"
     */
    private String collapseAdjacent(String expr, String op1, String op2) {
        // 拆分成 token
        java.util.List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) return "0";
        // 单个数字直接返回
        if (tokens.size() == 1) return tokens.get(0);

        java.util.List<String> out = new java.util.ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if ((t.equals(op1) || t.equals(op2)) && out.size() >= 1 && i + 1 < tokens.size()) {
                String prev = out.remove(out.size() - 1);
                double left = Double.parseDouble(prev);
                String right = tokens.get(++i);
                double r = Double.parseDouble(right);
                double computed;
                if (t.equals(op1) && "*".equals(op1)) computed = left * r;
                else if (t.equals(op2) && "/".equals(op2)) {
                    if (r == 0) throw new ArithmeticException("除数不能为 0");
                    computed = left / r;
                } else if (t.equals(op1) && "+".equals(op1)) computed = left + r;
                else if (t.equals(op2) && "-".equals(op2)) computed = left - r;
                else {
                    // 不属于当前阶段，保留
                    out.add(prev);
                    out.add(t);
                    i++;
                    continue;
                }
                out.add(formatNumber(computed));
            } else {
                out.add(t);
            }
            i++;
        }
        return String.join("", out);
    }

    /**
     * 把表达式拆成 [数字, 操作符, 数字, 操作符, 数字] 的形式
     * 支持一元负号（如 -3+5、3+-5）
     */
    private java.util.List<String> tokenize(String expr) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+' || c == '-' || c == '*' || c == '/') {
                String num = current.toString();
                if (!num.isEmpty()) {
                    tokens.add(num);
                    current.setLength(0);
                } else if (c == '-' && (tokens.isEmpty() || isOperator(tokens.get(tokens.size() - 1)))) {
                    // 一元负号：与前一个 token 是操作符时合并
                    current.append(c);
                    continue;
                }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private boolean isOperator(String s) {
        return "+".equals(s) || "-".equals(s) || "*".equals(s) || "/".equals(s);
    }

    private double factorial(int n) {
        if (n < 0) throw new IllegalArgumentException("阶乘不接受负数");
        if (n > 20) throw new IllegalArgumentException("阶乘 n 不能超过 20 (避免溢出)");
        long f = 1;
        for (int i = 2; i <= n; i++) f *= i;
        return f;
    }

    private String extract(String s, int prefixLen) {
        return s.substring(prefixLen, s.length() - 1);
    }

    // ============== unit conversion ==============

    private Double convertUnits(double value, String category, String from, String to) {
        if (from.equalsIgnoreCase(to)) return value;
        switch (category.toLowerCase()) {
            case "length": return lengthToMeter(value, from) / lengthToMeter(1.0, to);
            case "weight": return weightToGram(value, from) / weightToGram(1.0, to);
            case "storage": return storageToByte(value, from) / storageToByte(1.0, to);
            case "time": return timeToSecond(value, from) / timeToSecond(1.0, to);
            case "temperature": return convertTemperature(value, from, to);
            default: return null;
        }
    }

    private double lengthToMeter(double v, String unit) {
        switch (unit.toLowerCase()) {
            case "mm": return v / 1000.0;
            case "cm": return v / 100.0;
            case "m":  return v;
            case "km": return v * 1000.0;
            case "inch": return v * 0.0254;
            case "ft":   return v * 0.3048;
            case "mile": return v * 1609.344;
            default: throw new IllegalArgumentException("不支持的长度单位: " + unit);
        }
    }

    private double weightToGram(double v, String unit) {
        switch (unit.toLowerCase()) {
            case "mg": return v / 1000.0;
            case "g":  return v;
            case "kg": return v * 1000.0;
            case "ton": case "t": return v * 1_000_000.0;
            case "lb": return v * 453.592;
            case "oz": return v * 28.3495;
            default: throw new IllegalArgumentException("不支持的重量单位: " + unit);
        }
    }

    private double storageToByte(double v, String unit) {
        switch (unit.toLowerCase()) {
            case "b":   return v;
            case "kb":  return v * 1024.0;
            case "mb":  return v * 1024.0 * 1024.0;
            case "gb":  return v * 1024.0 * 1024.0 * 1024.0;
            case "tb":  return v * 1024.0 * 1024.0 * 1024.0 * 1024.0;
            default: throw new IllegalArgumentException("不支持的存储单位: " + unit);
        }
    }

    private double timeToSecond(double v, String unit) {
        switch (unit.toLowerCase()) {
            case "s": case "sec": case "second": return v;
            case "min": case "minute": return v * 60.0;
            case "h": case "hour": return v * 3600.0;
            case "d": case "day": return v * 86400.0;
            case "w": case "week": return v * 604800.0;
            default: throw new IllegalArgumentException("不支持的时间单位: " + unit);
        }
    }

    private Double convertTemperature(double value, String from, String to) {
        double celsius;
        switch (from.toLowerCase()) {
            case "c": case "celsius": celsius = value; break;
            case "f": case "fahrenheit": celsius = (value - 32) * 5.0 / 9.0; break;
            case "k": case "kelvin": celsius = value - 273.15; break;
            default: return null;
        }
        switch (to.toLowerCase()) {
            case "c": case "celsius": return celsius;
            case "f": case "fahrenheit": return celsius * 9.0 / 5.0 + 32;
            case "k": case "kelvin": return celsius + 273.15;
            default: return null;
        }
    }

    // ============== helpers ==============

    private double toDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }

    private String getString(Map<String, Object> p, String key, String defaultValue) {
        Object v = p.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        BigDecimal bd = new BigDecimal(v, new MathContext(12));
        if (bd.scale() <= 0) return bd.toPlainString();
        return bd.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(k).append("=").append(v).append("\n"));
        return sb.toString().trim();
    }
}
