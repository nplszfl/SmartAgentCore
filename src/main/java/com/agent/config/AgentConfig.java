package com.agent.config;

import com.agent.core.model.ChatModel;
import com.agent.core.model.DeepSeekModelAdapter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent配置类
 */
@Configuration
public class AgentConfig {

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
