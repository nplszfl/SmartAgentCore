package com.agent.core.model;

import com.agent.core.memory.Memory;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * ChatModel接口 - 统一的大模型调用接口
 */
public interface ChatModel {

    /**
     * 聊天补全
     */
    ModelResponse chat(List<Memory.Message> messages);

    /**
     * 聊天补全（带参数）
     */
    ModelResponse chat(List<Memory.Message> messages, Map<String, Object> parameters);

    /**
     * 获取模型名称
     */
    String getModelName();

    /**
     * 默认实现 - 简单的文本补全
     */
    static ChatModel of(String modelName, Function<List<Memory.Message>, ModelResponse> chatFn) {
        return new ChatModel() {
            @Override
            public ModelResponse chat(List<Memory.Message> messages) {
                return chatFn.apply(messages);
            }

            @Override
            public ModelResponse chat(List<Memory.Message> messages, Map<String, Object> parameters) {
                return chatFn.apply(messages);
            }

            @Override
            public String getModelName() {
                return modelName;
            }
        };
    }
}
