# 关于本项目

目录结构：

> 注意：每次修改后都要同步下面的目录结构

```
BizAgent/
├── src/main/java/com/v1hz/bizagent/
│   ├── BizAcpAgent.java          # ACP 协议核心 handler（init/prompt/cancel 等）
│   ├── config/
│   │   └── AcpAgentConfig.java    # ACP agent 传输层配置（WebSocket/stdio）
│   ├── service/
│   │   ├── AgentService.java      # ReactAgent 构建 + 工具注册 + 拦截器装配
│   │   ├── ChatService.java       # Agent 流式对话核心
│   │   ├── SessionService.java    # Session CRUD
│   │   └── ToolService.java       # 工具注册中心 + ToolCallUpdateChain（ACP 通知链）
│   ├── tool/
│   │   ├── BaseTool.java          # 工具接口（定义 getAllowedToolNames）
│   │   ├── BashTools.java         # Bash 命令执行（对接 ACP 终端协议）
│   │   ├── FileSystemTools.java   # 文件读写搜索
│   │   └── DateTimeTools.java     # 时间日期
│   │   └── schema/input/
│   │       └── BashInputSchema.java # Bash 工具入参（command + timeoutSeconds）
│   ├── interceptor/
│   │   └── ToolStatusInterceptor.java  # 工具状态通知 + 权限检查（PENDING → check → IN_PROGRESS → 执行 → COMPLETED）
│   ├── component/
│   │   └── DeepSeekChatModel.java # DeepSeek 自定义 ChatModel
│   ├── entity/
│   │   └── Session.java           # 会话实体（含 cwd 等字段）
│   ├── constants/
│   │   ├── AcpConstants.java      # ACP 常量
│   │   └── enums/
│   │       ├── PermissionOptionEnum.java  # 权限选项枚举
│   │       ├── SessionModeEnum.java       # 会话模式
│   │       ├── SessionModelEnum.java      # 模型枚举
│   │       ├── SessionStatusEnum.java     # 会话状态
│   │       └── SessionUpdateEnum.java     # ACP 更新类型
│   ├── dto/                       # DTO / 响应结构
│   ├── exception/                 # 业务异常
│   └── repository/                # 数据访问
├── acp/
│   ├── java-sdk/                  # ACP Java SDK（本地源码依赖）
│   ├── agent-client-protocol/     # ACP 协议规范（JSON Schema）
│   └── acp-java-tutorial/         # ACP Java 教程示例
├── docs/
│   ├── spring-ai/                 # Spring AI 文档
│   └── spring-ai-alibaba/         # Spring AI Alibaba 文档
└── AGENTS.md                      # 本文件
```

我正在使用 spring-ai 实现一个 agent

我的计划是根据 ACP 协议，实现一个完整对接 ACP 的 agent

# 待办

- [x] 实现 session 管理
  - [x] 新建 new
  - [x] 列表 list
  - [x] 加载 load
  - [x] 恢复 resume
  - [x] 关闭 close
  - [x] 取消 cancel
- [x] 正确处理agent的流式回答
  - [x] 思考
  - [x] 回答
  - [x] 工具调用
- [x] 工具
  - [x] 请求工具调用权限
  - [x] 终端命令
  - [x] 文件增删改查
  - [x] 白名单 / 权限分级
- [x] 切换模型
- [ ] 上下文工程
  - [x] 注入 session 的 cwd 到系统提示词
  - [x] 工具执行时自动使用 session cwd
  - [ ] 根据模式定制系统提示词
  - [ ] 记忆管理
- [ ] MCP 服务
- [ ] harness 工程
  - [ ] skills
  - [ ] AGENT.md
- [ ] 处理用户的消息附件

# 可以查阅的文档

- `docs/spring-ai/` — Spring AI 框架文档
- `docs/spring-ai-alibaba/` — Spring AI Alibaba 框架文档
- `acp/java-sdk/` — ACP Java SDK 源码
- `acp/agent-client-protocol/` — ACP 协议规范（包含文档和源码）
- `AGENTS.md` - 本文档，用于了解本项目和编码规范

# 编码约定

## 通用原则

- 保持逻辑在一个方法内，除非该片段具有独立的复用价值或需要组合。
- 不要提前提取仅使用一次的辅助方法。应在调用处直接内联，除非辅助方法会被多次使用、隐藏了复杂的边界，或者拥有清晰且能改善调用者可读性的独立名称。
- 尽量避免 `try/catch`，优先使用 `Optional` 或其他安全的错误处理方式。
- 避免使用 `Object` 或未限定的泛型；始终使用具体的类型。
- 当右侧表达式类型明确时，使用 `var` 进行局部变量类型推断。
- 优先使用 `Stream` API (`filter`, `map`, `flatMap`) 替代传统的 `for` 循环；在 `filter` 后配合类型转换以保持下游的类型推断准确。
- 减少变量总数：若某值只使用一次，考虑在调用处内联。

```java
// 好
var journal = Files.readString(Path.of(dir, "journal.json"));

// 差
var journalPath = Path.of(dir, "journal.json");
var journal = Files.readString(journalPath);
```

## 变量

- 局部变量优先使用 `final`。
- 使用三元运算符或早返回代替重新赋值。

```java
// 好
final var foo = condition ? 1 : 2;

// 差
int foo;
if (condition) {
    foo = 1;
} else {
    foo = 2;
}
```

## 控制流

- 避免 `else` 语句，优先采用早返回模式。
- 尽可能减少缩进，增强可读性

```java
// 好
public String foo() {
    if (condition) return "a";
    return "b";
}

// 差
public String foo() {
    if (condition) {
        return "a";
    } else {
        return "b";
    }
}
```

## 复杂逻辑

- 当方法包含多个校验分支或辅助步骤时，将主方法写为"快乐路径"，并将细节拆分成下方的小型私有方法。

```java
// 好
public Thing loadThing(Object input) {
    var config = requireConfig(input);
    var metadata = readMetadata(input);
    return createThing(config, metadata);
}

private Config requireConfig(Object input) { ... }
private Metadata readMetadata(Object input) { ... }
```

- 将辅助方法放在它们支持的代码附近，通常放在使用它们的主方法下方。
- 不要将简单表达式过度抽象为多个一次性辅助方法；仅当它能命名一个真实概念（如 `requireConfig` 或 `readMetadata`）时才抽取。
- 辅助方法不应引入不必要的副作用；同步的解析、验证和对象构建应保持同步。
- 为**非显而易见的约束和令人意外的行为**添加注释，而不是为直白的赋值或控制流添加注释。

## 测试

- 尽量避免使用 mock。测试应覆盖实际实现，不要在测试中重复业务逻辑。
- 测试不能在仓库根目录直接运行；需要从模块目录执行（例如 `./gradlew :module:test` 或 `mvn test -pl module`）。

## 静态检查

- 始终通过构建工具运行编译与静态分析（如 `./gradlew build` 或 `mvn verify`），不要直接调用 `javac`。

## 注释

- 每次编码，都应该保证有完整、易懂、易读的注释
- 修改代码后也要注意修改对应的注释
