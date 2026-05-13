package com.agent.core.tool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册器
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    /**
     * 注册工具
     */
    public ToolRegistry register(Tool tool) {
        tools.put(tool.getName(), tool);
        return this;
    }

    /**
     * 注册多个工具
     */
    public ToolRegistry register(Tool... toolList) {
        for (Tool tool : toolList) {
            register(tool);
        }
        return this;
    }

    /**
     * 获取工具
     */
    public Tool get(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有工具
     */
    public java.util.List<Tool> getAll() {
        return new java.util.ArrayList<>(tools.values());
    }

    /**
     * 移除工具
     */
    public Tool remove(String name) {
        return tools.remove(name);
    }

    /**
     * 检查工具是否存在
     */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /**
     * 清空所有工具
     */
    public void clear() {
        tools.clear();
    }

    public int size() {
        return tools.size();
    }
}
