# SmartAgent Core

> 通用Java Agent框架 | Universal Java Agent Framework

[![Java Version](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M4-orange.svg)](https://spring.io/projects/spring-ai)

## 特性

- 🔧 **Tool System** - 灵活的工具系统，支持自定义工具注册和执行
- 🧠 **Memory** - 对话记忆管理，支持上下文窗口控制
- 🤖 **Multi-Agent** - 支持多种Agent类型（ReAct等）
- 🔗 **Multi-Model** - 支持多种大模型（DeepSeek、OpenAI等）
- 🚀 **Easy to Use** - 简洁的API设计，快速集成

## 架构

```
┌─────────────────────────────────────────────────────────┐
│                        Agent                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  ReAct   │  │  Plan    │  │  Simple  │  ...         │
│  └──────────┘  └──────────┘  └──────────┘              │
├─────────────────────────────────────────────────────────┤
│                      Core                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │  Tool    │  │ Memory   │  │  Model   │              │
│  │ Registry │  │ Manager  │  │ Adapter  │              │
│  └──────────┘  └──────────┘  └──────────┘              │
├─────────────────────────────────────────────────────────┤
│                   Model Layer                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ DeepSeek │  │  OpenAI  │  │  Others  │              │
│  └──────────┘  └──────────┘  └──────────┘              │
└─────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.agent</groupId>
    <artifactId>smartagent-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置API Key

```yaml
# application.yml
agent:
  model:
    deepseek:
      api-key: your-deepseek-api-key
      model: deepseek-chat
```

### 3. 使用Agent

```java
@Autowired
private ChatModel chatModel;

// 创建Agent
ReActAgent agent = ReActAgent.builder()
    .name("my-agent")
    .description("我的智能助手")
    .systemPrompt("你是一个有用的助手")
    .toolRegistry(new ToolRegistry()
        .register(new SearchTool())
        .register(new CalculatorTool()))
    .build();

// 创建Executor
AgentExecutor executor = new AgentExecutor(chatModel);

// 执行
AgentExecutor.AgentResponse response = executor.execute(
    agent,
    "帮我计算 2+2",
    new HashMap<>()
);

System.out.println(response.content());
```

### 4. REST API

启动服务后访问：

```bash
# 执行Agent
curl -X POST http://localhost:8080/api/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"input": "今天天气怎么样？", "tools": ["search"]}'

# 简单聊天
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"input": "你好"}'

# 获取工具列表
curl http://localhost:8080/api/agent/tools
```

## 内置工具

| 工具 | 描述 | 参数 |
|------|------|------|
| search | 搜索互联网 | query, limit |
| calculator | 数学计算 | expression |
| datetime | 日期时间 | format, timezone |

## 项目结构

```
smartagent-core/
├── src/main/java/com/agent/
│   ├── core/                    # 核心模块
│   │   ├── agent/              # Agent接口和实现
│   │   ├── tool/               # 工具系统
│   │   ├── memory/            # 记忆系统
│   │   ├── model/             # 模型适配器
│   │   └── executor/          # 执行器
│   ├── api/                   # REST API
│   ├── config/                # 配置类
│   └── SmartAgentApplication.java
├── src/main/resources/
│   └── application.yml
└── src/test/java/             # 单元测试
```

## 设计模式

### ReAct Agent

ReAct = Reasoning + Acting

```
Thought → Action → Observation → Thought → ...
```

1. **Thought**: 分析问题，决定下一步行动
2. **Action**: 调用工具或生成回复
3. **Observation**: 观察工具返回结果
4. 循环直到完成任务

### Tool Registry

```java
ToolRegistry registry = new ToolRegistry();
registry.register(new SearchTool());
registry.register(new CalculatorTool());

// 获取工具
Tool tool = registry.get("search");

// 检查工具
if (registry.has("search")) {
    // ...
}
```

### Memory Management

```java
// 创建记忆
ConversationMemory memory = new ConversationMemory(100); // 最多100条

// 添加消息
memory.addUserMessage("你好");
memory.addAssistantMessage("有什么可以帮助你的？");

// 获取历史
List<Message> history = memory.getMessages();
List<Message> recent = memory.getRecentMessages(10);
```

## 扩展开发

### 自定义工具

```java
public class MyTool implements Tool {
    
    @Override
    public String getName() {
        return "my-tool";
    }
    
    @Override
    public String getDescription() {
        return "我的自定义工具";
    }
    
    @Override
    public String getParameterSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "param1": {"type": "string"}
                },
                "required": ["param1"]
            }
            """;
    }
    
    @Override
    public ToolResult execute(Map<String, Object> parameters) {
        // 业务逻辑
        return ToolResult.success("结果");
    }
}
```

### 自定义模型适配器

```java
public class MyModelAdapter implements ChatModel {
    
    @Override
    public ModelResponse chat(List<Message> messages) {
        // 调用你的模型
        return ModelResponse.builder()
            .content("响应内容")
            .done(true)
            .build();
    }
    
    @Override
    public String getModelName() {
        return "my-model";
    }
}
```

## License

MIT License
