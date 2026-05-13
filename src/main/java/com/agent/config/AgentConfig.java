package com.agent.config;

import com.agent.core.model.ChatModel;
import com.agent.core.model.DeepSeekModelAdapter;
import com.agent.core.model.MiniMaxModelAdapter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent配置类
 */
@Configuration
public class AgentConfig {

    /**
     * MiniMax模型配置（默认使用）
     */
    @Bean
    @ConfigurationProperties(prefix = "agent.model.minimax")
    public MiniMaxProperties minimaxProperties() {
        return new MiniMaxProperties();
    }

    @Bean
    public ChatModel minimaxChatModel(MiniMaxProperties properties) {
        return new MiniMaxModelAdapter(
            properties.getApiKey(),
            properties.getModel(),
            properties.getBaseUrl()
        );
    }

    /**
     * DeepSeek模型配置
     */
    @Bean
    @ConfigurationProperties(prefix = "agent.model.deepseek")
    public DeepSeekProperties deepSeekProperties() {
        return new DeepSeekProperties();
    }

    @Bean
    public ChatModel deepSeekChatModel(DeepSeekProperties properties) {
        return new DeepSeekModelAdapter(
            properties.getApiKey(),
            properties.getModel(),
            properties.getBaseUrl()
        );
    }

    /**
     * 默认的ChatModel Bean（可以切换使用哪个模型）
     */
    @Bean
    public ChatModel chatModel(MiniMaxProperties minimaxProperties, DeepSeekProperties deepSeekProperties) {
        // 优先使用MiniMax
        if (minimaxProperties.getApiKey() != null && !minimaxProperties.getApiKey().isEmpty()) {
            return new MiniMaxModelAdapter(
                minimaxProperties.getApiKey(),
                minimaxProperties.getModel(),
                minimaxProperties.getBaseUrl()
            );
        }
        // 备用DeepSeek
        if (deepSeekProperties.getApiKey() != null && !deepSeekProperties.getApiKey().isEmpty()) {
            return new DeepSeekModelAdapter(
                deepSeekProperties.getApiKey(),
                deepSeekProperties.getModel(),
                deepSeekProperties.getBaseUrl()
            );
        }
        throw new RuntimeException("请配置至少一个模型API Key");
    }

    /**
     * MiniMax配置属性
     */
    public static class MiniMaxProperties {
        private String apiKey;
        private String model = "abab6.5s-chat";
        private String baseUrl = "https://api.minimax.chat";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    /**
     * DeepSeek配置属性
     */
    public static class DeepSeekProperties {
        private String apiKey;
        private String model = "deepseek-chat";
        private String baseUrl = "https://api.deepseek.com";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }
}
