# 关于本项目

参阅 [README](README.md)

# 可以查阅的文档

- `docs/spring-ai/` — Spring AI 框架文档
- `docs/spring-ai-alibaba/` — Spring AI Alibaba 框架文档
- `docs/agent-client-protocol/` — ACP 协议文档
- `sdk-ref/opencode/` — OpenCode 参考实现（TypeScript），用于工具设计与 ACP 集成参考
- `AGENTS.md` - 本文档，用于了解本项目和编码规范

- `sdk-ref/java-sdk` - ACP 的 JavaSDK

# 编码约定

## 通用原则

- 保持逻辑在一个方法内，除非该片段具有独立的复用价值或需要组合。
- 不要提前提取仅使用一次的辅助方法。应在调用处直接内联，除非辅助方法会被多次使用、隐藏了复杂的边界，或者拥有清晰且能改善调用者可读性的独立名称。
- 尽量避免 `try/catch`，优先使用 `Optional` 或其他安全的错误处理方式。
- 避免使用 `Object` 或未限定的泛型；始终使用具体的类型。
- 当右侧表达式类型明确时，使用 `var` 进行局部变量类型推断。
- 优先使用 `Stream` API (`filter`, `map`, `flatMap`) 替代传统的 `for` 循环；在 `filter` 后配合类型转换以保持下游的类型推断准确。
- 减少变量总数：若某值只使用一次，考虑在调用处内联。
- 每次编码后，都必须编写测试来确保这次工作的产物没有问题

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
- 尽可能减少缩进。

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

# 项目架构模式

## ACP 工具通知链

所有工具调用通过 `ToolStatusInterceptor` 统一管理 ACP 通知：

1. `ToolService.createUpdateChain(sessionId, toolCallId, toolName, arguments)` — 根据工具名解析入参，设置 title/kind/locations/diffs
2. 链式发送：`pending()` → `check()` → `inProgress()` → 执行 → `completed()` / `failed()`

新增工具只需要：
- 创建 `XxxInputSchema` 类（`tool/schema/input/` 下）
- 创建工具实现类实现 `BaseTool` 接口
- 在 `ToolService.createUpdateChain()` 的 switch 中添加分支
- 在 `ToolService.getTools()` 中注册

## 工具入参设计

所有工具使用 `XxxInputSchema` 作为唯一入参（通过 `@ToolParam` 注解），遵循 `BashInputSchema` 命名模式。`ToolService.createUpdateChain` 通过 `SchemaParser.parse(arguments, XxxInputSchema.class)` 解析参数，从中提取 filePath、oldContent、newContent 等字段设置 ACP 通知的 locations 和 diff 内容。

## ACP 通知内容类型

| 工具类型 | content | 来源 |
|---------|---------|------|
| executeBash | ToolCallTerminal | 运行结果（截断 10K） |
| writeFile/editFile | ToolCallDiff | createUpdateChain 中从 schema + 磁盘旧内容构造 |
| readFile/listDirectory/其他 | ToolCallContentBlock (TextContent) | 运行结果（截断 50K） |

`content` 和 `locations` 字段帮助客户端在 IDE 中高亮文件并展示变更 diff。
