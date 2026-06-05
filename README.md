## 关于本仓库

我正在使用 spring-ai (alibaba) 实现一个对接 ACP (Agent Client Protocol) 的 agent。

## 目录结构

```
AcpAgent/
├── src/main/java/com/v1hz/acpagent/
│   ├── AcpAgent.java                  # ACP 协议核心 handler（init/prompt/cancel 等）
│   ├── config/
│   │   └── AcpAgentConfig.java        # ACP agent 传输层配置（WebSocket/stdio）+ Bean 装配
│   ├── service/
│   │   ├── AgentService.java          # ReactAgent 构建 + 工具注册 + hook/拦截器装配
│   │   ├── ChatService.java           # Agent 流式对话核心
│   │   ├── SessionService.java        # Session CRUD
│   │   ├── SessionUpdateService.java  # ACP 消息回放（历史消息 → session/update 通知）
│   │   ├── CancellationService.java   # prompt/工具取消管理（线程追踪 + 中断）
│   │   ├── McpService.java            # MCP 客户端生命期管理（init/close/tool 回调）
│   │   └── ToolService.java           # 工具注册中心 + ToolCallUpdateChain（ACP 通知链）
│   ├── hook/
│   │   └── AgentsMdHook.java          # 读取用户级/项目级 AGENTS.md 并注入 system prompt
│   ├── tool/
│   │   ├── BaseTool.java              # 工具接口（定义 getAllowedToolNames）
│   │   ├── BashTools.java             # Bash 命令执行 + 中断/超时/子进程清理
│   │   ├── FileSystemTools.java       # 文件读写搜索编辑（行号驱动）
│   │   ├── MiscTools.java             # 通用工具（日期时间等）
│   │   ├── schema/input/
│   │   │   ├── BashInputSchema.java   # Bash 工具入参（command + timeoutSeconds）
│   │   │   ├── ReadInputSchema.java   # 读取文件入参（filePath + offset + limit）
│   │   │   ├── WriteInputSchema.java  # 写入文件入参（filePath + content）
│   │   │   ├── EditInputSchema.java   # 编辑文件入参（filePath + oldContent + newContent + replaceAll）
│   │   │   └── ListDirectoryInputSchema.java  # 列表目录入参（path）
│   │   └── schema/utils/
│   │       └── SchemaParser.java      # JSON 入参解析（根据类名推导 key）
│   ├── interceptor/
│   │   └── ToolStatusInterceptor.java # 工具状态通知 + 权限检查 + 取消支持（PENDING → check → IN_PROGRESS → 执行 → COMPLETED/FAILED）
│   ├── component/
│   │   └── DeepSeekChatModel.java     # DeepSeek 自定义 ChatModel
│   ├── entity/
│   │   └── Session.java               # 会话实体（含 cwd 等字段）
│   ├── constants/
│   │   ├── AcpConstants.java          # ACP 常量
│   │   └── enums/                     # 权限、会话模式、模型、状态、更新类型枚举
│   ├── dto/                           # DTO / 响应结构
│   ├── exception/                     # 业务异常
│   └── repository/                    # 数据访问
├── docs/
│   ├── spring-ai/                     # Spring AI 文档
│   ├── spring-ai-alibaba/             # Spring AI Alibaba 文档
│   └── agent-client-protocol/         # ACP 协议文档
├── sdk-ref/
│   ├── java-sdk/                      # ACP Java SDK 参考
│   └── opencode/                      # OpenCode 参考实现（TypeScript，用于工具设计和 ACP 集成参考）
├── AGENTS.md                          # 项目级 Agent 指令
└── README.md                          # 本文件
```

## 上下文与技能

`AgentService` 在创建 `ReactAgent` 时会装配两类上下文扩展：

- `SkillsAgentHook`：从 `~/{acpagent.home-dir}/skills` 和 `{cwd}/{acpagent.home-dir}/skills` 加载 skills，并注册 `read_skill` 工具。
- `AgentsMdHook`：从 `~/{acpagent.home-dir}/AGENTS.md` 和 `{cwd}/AGENTS.md` 读取指令，并追加到模型的 system prompt。

`AgentsMdHook` 会显式区分用户级和项目级内容，并在两个路径指向同一文件时只注入一次。

## 待办

- [x] 实现 session 管理
  - [x] 新建 new
  - [x] 列表 list
  - [x] 加载 load（含历史消息回放）
  - [x] 恢复 resume
  - [x] 关闭 close
  - [x] 取消 cancel（含工具中断）
- [x] 正确处理 agent 的流式回答
  - [x] 思考块
  - [x] 回答块
  - [x] 工具调用通知（PENDING → IN_PROGRESS → COMPLETED + locations + diffs）
- [x] 工具
  - [x] 请求工具调用权限
  - [x] Bash 命令执行（超时 + 中断 + 子进程清理）
  - [x] 文件增删改查
  - [x] 白名单 / 权限分级
  - [x] ACP 通知添加 locations（文件位置）和 diff（变更对比）
- [x] 切换模型
- [ ] 上下文工程
  - [x] 注入 session 的 cwd 到系统提示词
  - [x] 工具执行时自动使用 session cwd
  - [ ] 根据模式定制系统提示词
  - [ ] 记忆管理
- [x] MCP 服务
- [x] harness 工程
  - [x] skills
  - [x] AGENTS.md
- [ ] 处理用户的消息附件
