# Agent 平台架构：llm 基础框架 + 业务注入

> 本文档是全系统 AI 应用的架构基准。以后做任何 AI 应用，都遵循同一分层：
> **llm 模块提供基础框架（引擎/会话/流程/组件契约），业务模块（如 insight）注入业务
> （提示词/工具/流程定义/前端子组件），继承即得开箱即用的前后端能力。**
> insight 是第一个完整示范：资产生成（flow 型 + 自主型）与智能问数（对话型）全部以注入方式接入。
>
> **前端同一原则**：chatbi 已基本完成对话形态的全部基础能力——基础部分抽离为父组件
> （AgentChat），业务部分以子组件形式外挂在父组件上（renderers 注册 / slot 注入）；
> 智能问数是其第二个业务子组件。以后任何对话型 AI 应用前端都复用该父组件，只写业务子组件。

## 1. 架构原则

1. **两类 agent，统一抽象**：
   - **flow 编排型**：流程确定性可知，分两阶段——调试阶段（前端 DAG 画布开发编排）与
     运行阶段（状态组件按节点展示进度/预计时间）。
   - **自主规划型**：只有运行场景，LLM 持工具自主决定顺序与拆分；前端以对话组件呈现，
     思考过程全程可见、结论时思考折叠、可暂停、可补指导语继续、可停止。
2. **llm 只做通用，不做业务**：不 import 任何业务模块；引擎、会话存储、控制器、通用工具库
   （流程调用）与前端基础组件都只依赖框架层（langchain4j/slf4j/spring/Redis）。
3. **业务只做注册，不做引擎**：业务模块以 `FlowDefinitionProvider` / `FlowNodeExecutor` /
   `AutonomousAgentDefinition` 三个扩展点注册能力（默认链、节点、提示词、工具工厂、收口），
   不复制循环、不复制编排、不复制组件。
4. **消息通道纯轮询**（会话事件流/状态/控制指令走 Redis，2s 增量拉取，分布式多实例天然兼容）；
   既有 SSE 流式对话链路（chatbi ask / 资产编辑页 AI 补全）保持现状——SSE 只用于短链路无状态
   形态（AI 接口三档分级的最轻档，须带 tick 活性心跳；长链路/需断线恢复一律轮询档）。
5. **守卫内置不依赖 LLM 自律**：落库/提案/查询工具在代码层校验（类型白名单/JSON 合法/引用防幻觉/
   敏感列拦截），拒绝原因回传 LLM 自纠。

## 2. 后端分层（llm 模块，包 `com.bidr.llm`）

### 2.1 已有基座
| 组件 | 包 | 职责 |
|---|---|---|
| `ToolAgentRunner` | `agent` | 自管工具循环（突破 AiServices 10 轮上限）、滑动窗口、触顶收口、停止收口 |
| `FlowEngine` | `flow` | DAG 执行：条件边、流式结点挂起/resume、环检测、轨迹埋点、收口 Listener |
| `FlowDefinitionProvider` | `flow` | 业务注册流程（skillCode/flowKey/默认链真源） |
| `FlowNodeExecutor` + `FlowNodeMeta` | `flow` | 业务注册节点类型 + schema 驱动属性表单元数据 |
| `FlowDefinitionStore` | `flow` | 编排持久化（自定义优先，回落内置默认链） |
| `SkillRatingService` | `skill` | 回答评价（点赞/点踩/统计） |
| `SseEventSender` | `sse` | 通用 SSE 事件发送器（不限 flow 链路，前身 FlowSseSender；七事件协议 conv/delta/tick/spec/msgid/done/error）：逐行拆发、线程安全（心跳与业务线程可并发写）、断连静默、活性心跳 startHeartbeat（周期推 tick，连接收口/断开自动停跳——AI 接口禁裸转圈的框架机制） |
| `LiveModelFactory`（Bean） | `model` | 流式进度模型装配工厂：自建 SSE 客户端+同步回落双通道统一装配点，代理/重试口径随 Bean 固化；业务侧只注入 live 回调与用途，无 Provider 时懒回落同步模型 |

### 2.2 本期新增（agent 会话层）
| 组件 | 职责 |
|---|---|
| `AgentSessionStore`（Redis/InMemory 双实现） | 事件流（seq 递增，type=run_start/round_start/tool_call/tool_result/llm_output/log/paused/resumed/guidance/finish/error/stopped）、会话状态（RUNNING/PAUSED/FINISHED/FAILED/STOPPED + 心跳）、暂停键/停止键（TTL 兜底） |
| `AgentSessionService` | 会话生命周期：start（查注册表起线程）/pause/resume(guidance)/stop/status/events(sinceSeq) |
| `AutonomousAgentDefinition` | 业务注册自主 agent：agentKey/skillCode/start(ctx,payload)/可选 onFinish |
| `AgentSessionController`（`/web/api/agent`） | 通用会话端点 + flow 调试端点（registry/detail/save/reset/traces，从业务 Controller 上提） |
| `ToolAgentRunner` 暂停原语 | 每轮 `listener.awaitResumeIfPaused()`：阻塞等恢复、恢复指导语注入为 UserMessage、停止优先于恢复 |
| `RunFlowTools`（通用工具库） | agent 会话内调 flow 链（深度 1 防递归、30s 超时、仅非流式链）——两类 agent 互操作的桥 |

### 2.3 flow 运行态增强
- `FlowContext.stopSupplier`：每节点执行前检查，命中即轨迹收口 stopped（接入业务 Redis 停止键）。
- `FlowNodeMeta.ConfigField.estimatedSecs`：节点预估耗时（运行态组件预计剩余时间数据源）。
- `FlowTraceRecorder` 补最近轨迹查询端点（SkillWorkbench 轨迹抽屉数据源）。

## 3. 业务注入三扩展点（insight 示范）

| 扩展点 | insight 注册 | 形态 |
|---|---|---|
| `FlowDefinitionProvider` + 节点执行器 | `AssetGenFlowDefinition`（skillCode=smart-agent，flowKey=asset-gen）+ SensitiveGate/Skeleton/Pair/AssetLlm/Finish 五执行器 | 资产生成 pipeline/skeleton 两模式统一一条链，条件边按 mode 跳过 |
| `AutonomousAgentDefinition` | `AssetAutonomousAgentDefinition`（asset-gen-autonomous） | 资产生成自主模式（探索+落库双工具，maxRounds=60，暂停/补语/停止） |
| `AutonomousAgentDefinition` | `MaintainQueryAgentDefinition`（maintain-query） | 维护问数自主 agent（探索→组装查询→真执行验证→提案落待审表，maxRounds=30） |

已有先例（chatbi）：`ChatBiRouteFlowDefinition`/`ChatBiAskFlowDefinition` + semantic/extract/routeCatalog
执行器 + `InsightChatBiFlowStore`，即 flow 型注入的既有示范。

### 3.1 工具库清单（agent 的"眼和手"）
| 工具库 | 工具 | 使用 agent |
|---|---|---|
| 探索（insight，已有） | describe_table / sample_rows / group_by_field / run_sql（只读+守卫） | 资产生成自主、维护问数 |
| 资产落库（insight，已有） | save_draft / get_draft / list_status / finish（守卫内置、按表合并） | 资产生成自主 |
| 语义查询执行（insight，新） | build_query（校验+dryRun 出 SQL 预览）/ execute_query（限行执行） | 维护问数 |
| 资产提案（insight，新） | propose_asset / list_proposals（落 SysSmartAgentProposal 待审表，审批闭环复用） | 维护问数 |
| 流程调用（llm，新，通用） | run_flow（同步调链取 output） | 任意 agent 复用确定性链 |

## 4. 前端三类组件（pm-hse-view，framework 层）

**后端两模式 × 前端三组件·开箱即用搭配矩阵**——业务注入一种后端模式，即同时获得对应的前端组件搭配，零开发：

| 后端模式 | 调试/开发阶段 | 运行阶段 |
|---|---|---|
| **flow 编排型** | **SkillWorkbench**：DAG 画布编排 + schema 属性表单 + 执行轨迹抽屉 | **AgentStages**：节点级进度（pending/running/ok/skipped/error/stopped）+ 实耗 + 预计剩余 + 内嵌停止 |
| **自主规划型** | ——（无调试阶段，仅运行态） | **AgentChat**：能丰富显示的对话组件——消息流（角色/时间/点赞/反馈收集）、思考过程全程可见结论时折叠、渲染器注册制外挂表格/图表/提案卡片、暂停/补语/停止 |

三组件均在 framework 层、不含任何业务；业务以外挂契约接入：后端注册扩展点定义，
前端经 renderers / #renderer slot / 适配器 props 注入。insight 已完整挂载验证（资产生成 flow 型 +
自主型、维护问数对话型）。

### 4.1 SkillWorkbench（DAG 调试形式，已有）
@vue-flow 画布 + palette + schema 属性表单；skillCode 驱动，业务零改造接入；
本期补「执行轨迹」抽屉（最近轨迹列表 + 节点时间线），形成调试反馈回路。

### 4.2 AgentChat（对话形式，**从 chatbi 抽离基础部分为父组件**）
chatbi 面板已基本完成对话形态的全部基础能力，按以下边界抽离：

**上移父组件（framework `components/common/agentChat/`，不含任何业务）**：
- 消息流框架：消息结构（id/role/content/status: pending|done|error|stopped/time/artifacts/props透传）、
  头像气泡布局、错误/等待/已停止态、时间戳格式化（当天 HH:mm、跨天补月-日）
- 评价交互：点赞/点踩按钮态（同值取消、busy 防连点）+ 点踩反馈收集弹层（意见文本提交）——api 经适配器注入
- 输入区：textarea（Enter 发送/Shift+Enter 换行）、发送/停止按钮随 loading 切换；
  自主会话扩展态：RUNNING→[暂停][停止]、PAUSED→[补语输入][继续][停止]
- 思考过程渲染（自主会话）：round/tool_call/tool_result 事件折叠为「思考过程」组，
  进行中展开、结论时自动折叠；paused/resumed/stopped 状态条
- 历史对话抽屉 UI 框架（列表/删除/恢复布局）；滚动管理；空态与推荐问题区
- **消息体渲染器注册制**：`renderers` prop（type→业务组件）+ 具名 slot 兜底，
  业务生成物（图表/表格/提案卡片）以外挂形式渲染在气泡内

**留在业务子组件（chatbi 挂载 AgentChat，注入业务）**：
- 路由选板（routeQuestion/全局模式 activeTableId 管理）
- chart-spec 处理（specMerge：语义目录/指标树/forge/merge → DashboardItem）
- 生成物渲染器：ChatBiChart / ChatBiTable 经 renderers 注册
- SSE 流式问答（useChatBiStream + chatbi api）；历史/评价 api 适配器
- 推荐问题来源（语义目录指标名）

**适配器契约**（父组件 props，业务注入）：
`send(question, ctx): MessageHandle`（回调 onDelta/onArtifact/onMessageId/onDone/onError，
SSE 与轮询会话两种实现皆可适配）、`history?: {list, remove, restore}`、`rate?: (messageId, rating) => Promise`。

智能问数（维护问数 agent）是第二个业务子组件：注入轮询会话适配器 + 表格/提案卡片渲染器。

### 4.3 AgentStages（阶段预计执行时间形式，新）
AsyncProcess 泛化：数据源为会话状态/flow 轨迹节点列表，节点级渲染
pending/running/ok/skipped/error/stopped + 实耗 + 预计剩余（Σ未执行节点 estimatedSecs 修正）；
内嵌停止按钮（props 开关）。资产生成 pipeline/skeleton 模式的运行态组件。

## 5. 会话数据流（纯轮询）

```
前端(2s events?sinceSeq) ──► AgentSessionController ──► AgentSessionStore(Redis)
                                                     ▲
业务 run 线程（AutonomousAgentDefinition.start / FlowEngine.execute）
   ├─ AgentSessionContext.emit(event) 逐事件 append
   ├─ 每轮 awaitResumeIfPaused()（暂停键阻塞；恢复指导语 consume 后注入下一轮）
   └─ 停止键 + 线程 interrupt 双通道收口
```

- 分布式：事件/暂停/停止全在 Redis，任意实例可查可控制；心跳超时判定失联转 STOPPED。
- 会话 TTL 24h；轨迹保留期沿用 FlowTraceRetentionProvider。

## 6. 新 AI 应用接入清单（开箱即用路径）

1. 后端：注册 `FlowDefinitionProvider`（flow 型）或 `AutonomousAgentDefinition`（自主型）+ 工具对象；
2. 前端：flow 型→SkillWorkbench 调试 + AgentStages 运行；自主型/对话型→AgentChat 子组件注入适配器与渲染器；
3. 评价/历史/轨迹/停止/暂停全部继承自框架，业务零开发。

**基准约束**：新 AI 应用不得在业务模块内复制工具循环/编排引擎/对话组件——
后端只注册扩展点，前端只写子组件；发现基础能力缺口时，能力下沉 llm/framework 层后再注入复用。
