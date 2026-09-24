# agent-runtime

外部 Agent Runtime 接入层：把 OpenHands / agent-system 等外部 runtime 接入框架，
让浏览器获得统一的过程树渲染与会话管理，**不需要知道背后是哪家 runtime**。

## 快速接入（其他项目）

### 1. 加依赖

```xml
<dependency>
    <groupId>com.bidr</groupId>
    <artifactId>agent-runtime</artifactId>
    <version>${bidr.core.agent.runtime.version}</version>
</dependency>
```

### 2. 配置（application.yml）

```yaml
my:
  agent:
    runtime:
      enabled: true                # 🔴 框架模块默认不装配，必须显式开启
      provider: openhands          # openhands | agent-system
      base-url: http://127.0.0.1:13100
      api-key: <OH_SESSION_API_KEY>
```

阈值以系统参数管理页（`AGENT_RUNTIME_*`）为准，此处仅为未配置时的回落默认。

### 3. 前端

复用框架 `AgentChatPanel` 统一壳 + `processTree` 统一过程树渲染器。
后端自动暴露 `/web/agent/rt/**` 端点（会话 CRUD + SSE 流 + 附件 + 取消）。

## SPI 边界（谁进 llm、谁进本模块）

| 包 | 仓 | 内容 |
|---|---|---|
| `com.bidr.llm.agent.runtime.spi` | core/llm | `AgentRuntimeProvider` / `RuntimeTurnLink` / `SessionCreateCmd` / `TurnOpenCmd` / `AgentRequestContext` |
| `com.bidr.llm.agent.runtime.dto` | core/llm | 契约 DTO（`AgentInfo` / `SessionInfo` / `TurnItem` / `TurnBlock` / `TurnPage` / `SubAgentInfo` / …） |
| `com.bidr.llm.agent.runtime.event` | core/llm | 规范过程事件词表（`RuntimeEvents`） |
| `com.bidr.agent.runtime.provider` | **本模块** | 各上游的 codec 与实现（OpenHands / agent-system） |
| `com.bidr.agent.runtime.relay` | **本模块** | 浏览器中转 controller 与流泵 |
| `com.bidr.agent.runtime.dao` | **本模块** | 会话归属映射表（`sys_agent_session`） |

**新接一家 runtime** = 在本模块写一个 `AgentRuntimeProvider` 实现 + 一个 codec，
把上游原生事件翻译成 `RuntimeEvents` 规范事件即可，前端与 core/llm 零改动。

## 文档

- [OpenHands 部署与对接](doc/openhands-部署与对接.md)（镜像/端口/挂载/env/profile/子 agent/已知坑）
- [OpenHands 接入形态决策](../../../../docs/openhands-接入形态决策.md)（项目仓，选型与决策记录）
- [开发里程碑跟踪](../../../../docs/开发里程碑跟踪.md)（项目仓，进度与决策 D1–D22）
