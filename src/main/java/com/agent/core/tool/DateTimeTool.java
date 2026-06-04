package com.agent.core.tool;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 日期时间工具 - 增强版，支持当前时间、日期算术、时区转换、工作日计算等业务场景。
 *
 * 支持的操作 (operation 参数):
 *   - now            获取当前时间（可指定时区和格式）
 *   - parse          解析日期字符串
 *   - format         将时间戳/字符串格式化为目标格式
 *   - add            日期算术 (e.g. 2025-01-01 + 7 days)
 *   - diff           计算两个时间之间的差值（天/小时/分钟）
 *   - convert        时区转换
 *   - business_days  计算两个日期之间的工作日数
 *   - weekday        获取日期是星期几
 *   - next_business_day 获取下一个工作日
 */
public class DateTimeTool implements Tool {

    private final String name = "datetime";
    private final String description = "日期时间工具：获取当前时间、解析/格式化日期、日期算术、时区转换、工作日计算等。";

    private static final Map<String, String> DEFAULT_FORMATS = new LinkedHashMap<>();
    static {
        DEFAULT_FORMATS.put("iso", "yyyy-MM-dd'T'HH:mm:ss");
        DEFAULT_FORMATS.put("date", "yyyy-MM-dd");
        DEFAULT_FORMATS.put("datetime", "yyyy-MM-dd HH:mm:ss");
        DEFAULT_FORMATS.put("short", "yyyy/MM/dd");
        DEFAULT_FORMATS.put("timestamp", "yyyyMMddHHmmss");
    }

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
        return """
        {
            "type": "object",
            "properties": {
                "operation": {
                    "type": "string",
                    "enum": ["now", "parse", "format", "add", "diff", "convert", "business_days", "weekday", "next_business_day"],
                    "description": "操作类型，默认 now"
                },
                "format": {
                    "type": "string",
                    "description": "目标格式（也支持预定义别名: iso, date, datetime, short, timestamp）"
                },
                "timezone": {
                    "type": "string",
                    "description": "时区，如 Asia/Shanghai，默认 Asia/Shanghai"
                },
                "date": {
                    "type": "string",
                    "description": "日期字符串（parse/format/weekday/next_business_day 操作需要）"
                },
                "from_date": {
                    "type": "string",
                    "description": "起始日期（diff/business_days/add 操作需要）"
                },
                "to_date": {
                    "type": "string",
                    "description": "结束日期（diff/business_days 操作需要）"
                },
                "amount": {
                    "type": "number",
                    "description": "算术数量（add 操作需要）"
                },
                "unit": {
                    "type": "string",
                    "enum": ["days", "hours", "minutes", "seconds", "weeks", "months", "years"],
                    "description": "算术单位（add 操作需要）"
                },
                "target_timezone": {
                    "type": "string",
                    "description": "目标时区（convert 操作需要）"
                },
                "source_timezone": {
                    "type": "string",
                    "description": "源时区（convert 操作需要，默认 UTC）"
                }
            }
        }
        """;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        try {
            String operation = getStringParam(parameters, "operation", "now");

            switch (operation) {
                case "now":                return doNow(parameters);
                case "parse":              return doParse(parameters);
                case "format":             return doFormat(parameters);
                case "add":                return doAdd(parameters);
                case "diff":               return doDiff(parameters);
                case "convert":            return doConvert(parameters);
                case "business_days":      return doBusinessDays(parameters);
                case "weekday":            return doWeekday(parameters);
                case "next_business_day":  return doNextBusinessDay(parameters);
                default:
                    return ToolResult.failure("未知操作: " + operation);
            }
        } catch (Exception e) {
            return ToolResult.failure("日期时间操作失败: " + e.getMessage());
        }
    }

    // ============== operation implementations ==============

    private ToolResult doNow(Map<String, Object> p) {
        String timezone = getStringParam(p, "timezone", "Asia/Shanghai");
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zone);

        String formatAlias = getStringParam(p, "format", "datetime");
        String pattern = resolveFormat(formatAlias);
        String formatted = now.format(DateTimeFormatter.ofPattern(pattern));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formatted", formatted);
        result.put("timezone", zone.getId());
        result.put("epoch_millis", now.toInstant().toEpochMilli());
        result.put("iso", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return ToolResult.success(formatMap(result));
    }

    private ToolResult doParse(Map<String, Object> p) {
        String date = getStringParam(p, "date", null);
        if (date == null || date.isBlank()) {
            return ToolResult.failure("date 参数必填");
        }
        String format = getStringParam(p, "format", null);
        String timezone = getStringParam(p, "timezone", "Asia/Shanghai");
        ZoneId zone = ZoneId.of(timezone);

        try {
            LocalDateTime parsed;
            if (format != null && !format.isBlank()) {
                parsed = LocalDateTime.parse(date, DateTimeFormatter.ofPattern(resolveFormat(format)));
            } else {
                parsed = parseFlexible(date);
            }
            ZonedDateTime zdt = parsed.atZone(zone);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("iso", zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            result.put("epoch_millis", zdt.toInstant().toEpochMilli());
            result.put("date", zdt.toLocalDate().toString());
            result.put("time", zdt.toLocalTime().toString());
            result.put("timezone", zone.getId());
            return ToolResult.success(formatMap(result));
        } catch (Exception e) {
            return ToolResult.failure("解析日期失败: " + e.getMessage());
        }
    }

    private ToolResult doFormat(Map<String, Object> p) {
        String date = getStringParam(p, "date", null);
        String format = getStringParam(p, "format", "datetime");
        if (date == null || date.isBlank()) {
            return ToolResult.failure("date 参数必填");
        }

        String pattern = resolveFormat(format);
        try {
            // 接受 ISO 字符串、epoch_millis、长整型时间戳
            ZonedDateTime zdt;
            if (date.matches("^\\d+$")) {
                long ts = Long.parseLong(date);
                zdt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault());
            } else {
                zdt = ZonedDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME);
            }
            return ToolResult.success(zdt.format(DateTimeFormatter.ofPattern(pattern)));
        } catch (Exception e) {
            return ToolResult.failure("格式化失败: " + e.getMessage());
        }
    }

    private ToolResult doAdd(Map<String, Object> p) {
        String fromDate = getStringParam(p, "from_date", null);
        Object amountObj = p.get("amount");
        String unit = getStringParam(p, "unit", "days");
        if (fromDate == null || fromDate.isBlank() || amountObj == null) {
            return ToolResult.failure("from_date 和 amount 必填");
        }

        LocalDateTime base = parseFlexible(fromDate);
        long amount = toLong(amountObj);

        LocalDateTime result;
        switch (unit.toLowerCase()) {
            case "seconds": result = base.plusSeconds(amount); break;
            case "minutes": result = base.plusMinutes(amount); break;
            case "hours":   result = base.plusHours(amount);   break;
            case "days":    result = base.plusDays(amount);    break;
            case "weeks":   result = base.plusWeeks(amount);   break;
            case "months":  result = base.plusMonths(amount);  break;
            case "years":   result = base.plusYears(amount);   break;
            default: return ToolResult.failure("不支持的单位: " + unit);
        }

        return ToolResult.success(result.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private ToolResult doDiff(Map<String, Object> p) {
        String fromDate = getStringParam(p, "from_date", null);
        String toDate = getStringParam(p, "to_date", null);
        String unit = getStringParam(p, "unit", "days");
        if (fromDate == null || toDate == null) {
            return ToolResult.failure("from_date 和 to_date 必填");
        }

        LocalDateTime start = parseFlexible(fromDate);
        LocalDateTime end = parseFlexible(toDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("to", end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.put("seconds", ChronoUnit.SECONDS.between(start, end));
        result.put("minutes", ChronoUnit.MINUTES.between(start, end));
        result.put("hours",   ChronoUnit.HOURS.between(start, end));
        result.put("days",    ChronoUnit.DAYS.between(start, end));
        return ToolResult.success(formatMap(result));
    }

    private ToolResult doConvert(Map<String, Object> p) {
        String date = getStringParam(p, "date", null);
        String sourceTz = getStringParam(p, "source_timezone", "UTC");
        String targetTz = getStringParam(p, "target_timezone", null);
        if (date == null || targetTz == null) {
            return ToolResult.failure("date 和 target_timezone 必填");
        }

        try {
            ZonedDateTime zdt;
            if (date.matches("^\\d+$")) {
                zdt = Instant.ofEpochMilli(Long.parseLong(date)).atZone(ZoneId.of(sourceTz));
            } else {
                LocalDateTime ldt = parseFlexible(date);
                zdt = ldt.atZone(ZoneId.of(sourceTz));
            }
            ZonedDateTime converted = zdt.withZoneSameInstant(ZoneId.of(targetTz));
            return ToolResult.success(converted.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (Exception e) {
            return ToolResult.failure("时区转换失败: " + e.getMessage());
        }
    }

    private ToolResult doBusinessDays(Map<String, Object> p) {
        String fromDate = getStringParam(p, "from_date", null);
        String toDate = getStringParam(p, "to_date", null);
        if (fromDate == null || toDate == null) {
            return ToolResult.failure("from_date 和 to_date 必填");
        }
        LocalDate start = LocalDate.parse(fromDate.substring(0, Math.min(10, fromDate.length())));
        LocalDate end = LocalDate.parse(toDate.substring(0, Math.min(10, toDate.length())));
        if (end.isBefore(start)) {
            LocalDate tmp = start; start = end; end = tmp;
        }

        long count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) count++;
        }
        return ToolResult.success(String.valueOf(count));
    }

    private ToolResult doWeekday(Map<String, Object> p) {
        String date = getStringParam(p, "date", null);
        if (date == null || date.isBlank()) {
            return ToolResult.failure("date 参数必填");
        }
        LocalDate d = LocalDate.parse(date.substring(0, Math.min(10, date.length())));
        String[] names = {"星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        int idx = d.getDayOfWeek().getValue() - 1;
        return ToolResult.success(names[idx] + " (" + d.getDayOfWeek() + ")");
    }

    private ToolResult doNextBusinessDay(Map<String, Object> p) {
        String date = getStringParam(p, "date", null);
        if (date == null || date.isBlank()) {
            return ToolResult.failure("date 参数必填");
        }
        LocalDate d = LocalDate.parse(date.substring(0, Math.min(10, date.length())));
        LocalDate next = d.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return ToolResult.success(next.toString());
    }

    // ============== helpers ==============

    private String getStringParam(Map<String, Object> p, String key, String defaultValue) {
        Object v = p.get(key);
        return v != null ? v.toString() : defaultValue;
    }

    private long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private String resolveFormat(String format) {
        if (format == null || format.isBlank()) return "yyyy-MM-dd HH:mm:ss";
        String lower = format.toLowerCase();
        return DEFAULT_FORMATS.getOrDefault(lower, format);
    }

    private LocalDateTime parseFlexible(String input) {
        String s = input.trim();
        try {
            if (s.length() == 10) {
                return LocalDate.parse(s).atStartOfDay();
            }
            if (s.length() == 19) {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            if (s.length() == 16) {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return LocalDateTime.parse(s.replace(" ", "T"));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析日期: " + input);
        }
    }

    private String formatMap(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> sb.append(k).append("=").append(v).append("\n"));
        return sb.toString().trim();
    }
}
