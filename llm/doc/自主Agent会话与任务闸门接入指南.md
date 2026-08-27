# llm 框架层业务接入指南（自主 Agent 会话 / 任务闸门）

> 适用模块：`core/llm`（`com.bidr.llm.agent.*`）
> 读者：需要在业务模块（如 insight）接入「自主规划型 agent 会话」或「全局单任务互斥」的开发同学
> 参考实现：insight 的 `AssetGenAgentDefinition`（资产自主生成）、`MaintainQueryAgentDefinition`（自主维护问数）

---

## 一、分层边界：框架给什么、业务做什么

框架层只给**通用原语**，不下沉任何业务概念（停止信号、进度协议、可续作语义属业务层）。

| 框架层（core/llm，开箱即用） | 业务层（自行实现） |
| --- | --- |
| 会话生命周期：start/pause/resume/stop/answer/status/events/active 列表 | 业务执行体：`AutonomousAgentDefinition`（或继承 `AbstractToolLoopAgent`） |
| 事件流 + 状态快照存储（Redis/内存自动装配，24h TTL） | 业务提示词（角色/硬约束/任务目标/输出 schema） |
| 心跳 20s + 查询侧 180s 失联判定 → STOPPED 兜底收口；断开策略（默认断开即停省 token） | 业务工具（`@Tool` 方法，含约定名工具，见 §2.4） |
| 终态收口：stages/plan 的 running 段自动置位（不再永久转圈） | 互斥/停止/续作语义（可复用 `AgentTaskGate` 做互斥） |
| 通用 REST 端点 `/web/api/agent/**`（含注册中心） | 业务入口 Controller（锁预检、参数组装等） |
| 工具循环引擎 `ToolAgentRunner`（停止/暂停钩子接线） | 阶段清单与计划待办的业务编排（何时声明/挑勾） |
| 内置规划纪律提示词 `AutonomousSystemPrompt` | |
| 通用 `ask_user` 工具 `AgentAskUserTool` | |
| 全局单任务闸门 `AgentTaskGate`（互斥 + 失联自愈） | |
| 通用历史对话落盘（`conversationQuestion` 钩子，归属码经 `conversationAgentCode` 解析默认 agentKey） | |

---

## 二、自主 Agent 会话接入

### 2.1 选型

- **单次 LLM 工具循环** → 继承 `AbstractToolLoopAgent<S>`（推荐）：模板基类包办停止映射、摘要写入、钩子接线、异常路径资源释放，只填钩子，「想写错都难」。
- **多阶段 / 桥接存量任务体**（进度记录、心跳、复合收口等）→ 直接实现 `AutonomousAgentDefinition`。
  参考：`insight/.../flow/AssetGenAgentDefinition`（桥接存量生成链）、`MaintainQueryAgentDefinition`。

### 2.2 方式 A：继承 AbstractToolLoopAgent（推荐）

定义类注册为 Spring Bean 即被 `AgentSessionService` 纳入注册表（`agentKey` 全局唯一，重复注册启动时抛错）。

```java
@Component
public class XxxAgent extends AbstractToolLoopAgent<XxxState> {

    @Override public String agentKey()    { return "xxx-autonomous"; }   // 建议 skill-业务 形式
    @Override public String displayName() { return "XX 自主生成"; }
    @Override public String skillCode()   { return "xxx"; }

    /** 循环前置：payload 校验（缺参抛出→FAILED）、抢闸门、开连接、构建任务态 */
    @Override
    protected XxxState prepare(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        agentTaskGate.acquire(TASK_KEY, INSTANCE_ID, TTL, BUSY_MSG);     // 互斥见 §三
        return new XxxState(...);
    }

    @Override protected ChatLanguageModel model(XxxState s, AgentSessionContext ctx, Map<String, Object> p) { ... }

    /** 系统提示词 = 仅框架内置规划纪律（system 槽不得沾任何业务内容） */
    @Override protected String systemPrompt(XxxState s, AgentSessionContext ctx, Map<String, Object> p) {
        return AutonomousSystemPrompt.PLANNING_DISCIPLINE;
    }

    /** 用户提示词：业务角色/硬约束 + 任务目标 + 上下文（运行期拼装，业务段拼在首部） */
    @Override protected String userPrompt(XxxState s, AgentSessionContext ctx, Map<String, Object> p) { ... }

    @Override
    protected List<Object> tools(XxxState s, AgentSessionContext ctx, Map<String, Object> p) {
        return List.of(new XxxTools(s, ctx),          // 业务工具（含约定名工具，见 §2.4）
                       new XxxPlanTools(ctx),          // submit_plan/start_plan_item/done_plan_item 桥接 ctx
                       new AgentAskUserTool(ctx));     // 框架通用 ask_user，直接复用
    }

    /** 资源释放：异常路径也保证执行（finally） */
    @Override protected void release(XxxState s) { agentTaskGate.release(TASK_KEY, INSTANCE_ID); }

    /** 可选：返回非空 → 会话收口后自动向通用历史对话写单轮对话（user=本返回值，assistant=结论摘要） */
    @Override public String conversationQuestion(Map<String, Object> payload) { return "…"; }
    /** 可选：对话归属 agentCode（默认 agentKey）；需归入动态命名空间码（如 smartquery:{code}，
     *  与同业务注册中心码同构、前端可按页面所选 agent 过滤历史）时覆写 */
    @Override public String conversationAgentCode(Map<String, Object> payload) { return "ns:{code}"; }
    /** 断开策略默认值（发起方可在 start 时覆盖，同一 agent 不同页面场景可差异化）：
     *  默认 STOP_ON_DETACH：断开超 60s 自动停止省 token，用户对话场景；
     *  功能型任务如配置/资产生成覆写 KEEP_RUNNING：后台继续，刷新后经活跃列表重连 */
    @Override public DetachPolicy detachPolicy() { return DetachPolicy.KEEP_RUNNING; }
    /** 可选：会话作用对象标识（落快照 subject，供活跃列表按业务维度定向重连，如业务 agentCode） */
    @Override public String sessionSubject(Map<String, Object> payload) { return "…"; }
}
```

要点：

- **Bean 是单例，钩子间禁放会话级字段**——会话状态一律走泛型 `S` 经参传递。
- `prepare` 抛异常 → 会话层收口 FAILED；`result.isStopped()` → 基类抛 `InterruptedException` → 会话层收口 STOPPED，无需业务处理。
- 结论摘要默认取循环最终文本，可覆写 `summary(...)` 定制。

### 2.3 方式 B：直接实现 AutonomousAgentDefinition

`start(ctx, payload)` 在 run 线程内执行，业务自组提示词/工具并驱动 `ToolAgentRunner.run(...)`。收口契约（与基类同口径，须自行保证）：

- 停止：`ctx.loopListener()` 直连引擎（shouldStop 接停止键+线程中断；awaitResumeIfPaused 暂停阻塞）；
  用户停止最终须以 `InterruptedException` 外溢，会话层据此收口 STOPPED。
- 摘要：`ctx.setSummary(...)`（FINISHED 时前端展示）。
- 异常直接抛出 → 会话层收口 FAILED。
- 资源释放放 `finally`；终态后补记录放 `onFinish(ctx, status, error)`（异常只记日志）。

### 2.4 会话上下文 AgentSessionContext 能力速查

| 分组 | 方法 | 说明 |
| --- | --- | --- |
| 事件上报 | `emit(type, payload)` / `log(line)` | 追加式过程事件（前端思考组/日志行数据源） |
| | `pushLive(text)` | 替换式流式实时内容（每秒级高频；终态传 null 清空） |
| 阶段 | `defineStages(AgentStage...)` | 开局声明清单（全 pending，可重复调用覆盖） |
| | `stageStart/stageDone/stageFail/stageSkip(key, detail)` | 推进阶段；未声明的 key 忽略记日志 |
| 计划待办 | `submitPlan(List<String>)` | 开局提交清单（id 自 1 重排） |
| | `startPlanItem(id)` / `donePlanItem(id, note)` | 标记执行中（至多一条）/ 挑勾 |
| | `planText()` / `planBrief()` | 工具回显供 LLM 掌握编号与进度 |
| 用户决策 | `askQuestion(question, options)` | 落快照发 QUESTION 事件，前端渲染问题卡片 |
| | `awaitAnswer(q, timeoutMillis)` | 阻塞等答（1s 醒查）；返回答案/null=跳过/`AWAIT_STOPPED`/`AWAIT_EXPIRED` 哨兵 |
| 控制原语 | `isStopRequested()` / `isPaused()` / `awaitResumeIfPaused()` | 停止键+中断双通道；暂停阻塞返回恢复指导语 |
| | `loopListener()` | 组合钩子直连 `ToolAgentRunner` |
| 其他 | `setSummary(text)` / `getPayload()` / `getSessionId()` | 结论摘要 / 启动参数 / 会话标识 |

### 2.5 工具契约约定名

`AutonomousSystemPrompt` 内置规划纪律引用五个**通用约定工具名**，框架不内置其实现（业务零绑定），由各业务按名注册 `@Tool` 并桥接 ctx：

| 约定名 | 桥接 | 说明 |
| --- | --- | --- |
| `submit_plan` | `ctx.submitPlan(...)` | 开局提交计划待办清单 |
| `start_plan_item` | `ctx.startPlanItem(id)` | 标记执行中 |
| `done_plan_item` | `ctx.donePlanItem(id, note)` | 完成挑勾 |
| `ask_user` | 直接复用 `AgentAskUserTool(ctx)` | 框架已提供实现，加入 tools 清单即可 |
| `finish` | 业务硬校验 + `ctx.setSummary(...)` | 首次调用做业务收口自查，发现问题回传修正 |

参考：`insight/.../service/AssetGenAgentTools`（计划待办与 finish 自查的完整桥接范式）。

### 2.6 通用端点（框架自带，业务零开发）

`AgentSessionController`，前缀 `/web/api/agent`：

| 端点 | 说明 |
| --- | --- |
| `GET /agents` | 注册中心清单（自主型 + flow 型） |
| `POST /session/start` | `{agentKey, payload, detachPolicy?}` → 返回 `AgentSessionState`（含 sessionId）；detachPolicy 可选，发起方按页面场景覆盖定义默认 |
| `POST /session/{id}/pause` / `resume` | 暂停 / 恢复（resume 可携补充指导语） |
| `POST /session/{id}/answer` | 作答 ask_user 问题（questionId/answer/skipped） |
| `POST /session/{id}/stop` | 停止（停止键跨实例 + 本实例线程中断双通道） |
| `GET /session/{id}/status` | 状态快照（stages/plan/questions/live/summary，2s 轮询载体） |
| `GET /session/{id}/events?sinceSeq=` | 事件流增量读取 |
| `GET /sessions/active?agentKey=` | 活跃会话列表（本人发起的非终态会话，新→旧）：刷新/重连场景数据源，快照带 `subject` 供业务维度定向重连 |
| `POST /session/{id}/rate` | 结论评价 |

业务入口 Controller 只需做业务预检（如闸门 `checkFree` 在途拦截）后透传 start，或前端直连通用端点。

### 2.7 免费获得的生命周期保障

- **失联兜底**：run 线程心跳 20s；查询侧发现非终态且心跳超 180s → 改写 STOPPED 并收口 stages/plan。
- **终态收口**（run 线程结束 / 失联判定两入口统一）：
  - stages：running→ok（FINISHED 补齐）/ error（FAILED）/ stopped（STOPPED），pending→skipped；
  - plan：running→done（FINISHED 补挑勾，无备注补「收口自动挑勾」）/ stopped，pending 保持原样。
  - 即：**LLM 漏挑勾、后端重启杀线程，清单/阶段条都不会永久转圈**。
- **停止幂等、暂停/恢复幂等**；停止后重开会话由业务自行定义续作语义（草稿保留等）。
- **分场景断开策略（`DetachPolicy`，发起方定义、定义层只声明默认）**：同一 agent 在不同页面场景需求不同（如测试页需重连、用户对话断开即停），故 start 时可传 `detachPolicy` 覆盖，解析结果落快照供心跳执行；前端 status 轮询即存活信号（独立轻键，不污染快照）。
  - `STOP_ON_DETACH`（默认，用户对话场景）：前端断开超 60s → 心跳任务自动停止会话省 token，收口文案「前端已断开，会话自动停止（节省资源，可重新发起）」；前端轮询常驻不随页签隐藏暂停（否则误判）。
  - `KEEP_RUNNING`（功能型任务，如配置/资产生成）：断开后台继续；页面重载后经 `GET /sessions/active` 找回 sessionId（按 `subject` 定向筛）重连 AgentChat 继续跟进。参考：`AgentAssetEdit.vue` 的 `reconnectAutonomous` 提示重连范式。

---

## 三、任务闸门 AgentTaskGate 接入

### 3.1 语义与边界

「全平台同时仅一个在途任务」的会话无关互斥原语：锁值存 `{属主令牌, 心跳}`，属主周期续期；属主失联（宕机/重启杀线程）后无人续期，各入口内置自愈当场强删残留锁，**无需干等 TTL**。

边界：仅互斥 + 活性自愈。**停止信号、进度协议、可续作语义属业务层**，不在本原语范围。

### 3.2 接入范式

注入即用（Redis/内存实现按类路径自动装配，`@ConditionalOnMissingBean` 可覆盖）：

```java
private static final String TASK_KEY = "asset-gen";                 // 业务自定义全局任务键
private static final String BUSY_MSG = "正在生成中，请稍候（或等待执行实例失联后自动解锁）";
private static final int TTL_SECONDS = 300;                          // > 心跳周期
private static final String INSTANCE_ID = /* JVM 级令牌（如启动 UUID） */;

// ① 任务发起：拿锁（占用且属主存活 → NoticeException(busyMessage)；属主失联 → 强删重试一次）
agentTaskGate.acquire(TASK_KEY, INSTANCE_ID, TTL_SECONDS, BUSY_MSG);

// ② 会话创建前预检（不拿锁）：在途拦截
agentTaskGate.checkFree(TASK_KEY, BUSY_MSG);

// ③ 任务线程周期续期（活动即存活信号）
agentTaskGate.heartbeat(TASK_KEY, INSTANCE_ID, TTL_SECONDS);

// ④ 停止/查询入口自愈（任意实例可调）：失联残留锁当场强删
agentTaskGate.forceUnlockIfOrphan(TASK_KEY);

// ⑤ 任务线程 finally 必释（仅属主匹配才删，防误删他实例/后继任务的锁）
agentTaskGate.release(TASK_KEY, INSTANCE_ID);
```

参考：`insight/.../service/SmartAgentAssetGenerateService`（两模式共用 `acquire` 锁点、停止/预检/进度查询三入口自愈的完整范式）。

### 3.3 避坑要点

1. **ownerToken 须 JVM 级**（INSTANCE_ID），不要用线程 ID——常见形态是请求线程拿锁、@Async 任务线程释锁。
2. **release 放任务线程 finally**；后端重启杀线程导致 finally 未跑时，靠失联自愈（④/①/② 内置）兜底，不靠 TTL 干等。
3. **失联阈值 > 心跳周期留足余量**：阈值内的心跳静默属正常间隙，绝不可强删（配置 `orphan-millis` 默认 90s）。
4. 占用提示经 kernel `NoticeException` 抛出，文案调用方传入（业务友好口径），前端请求层直接弹 details。
5. 多个互斥入口（如全体生成/单资产重生成）须**共用同一 TASK_KEY 与同一 acquire 点**，互斥才成立。

---

## 四、前端接入

| 组件/模块 | 路径 | 用途 |
| --- | --- | --- |
| `AgentChat` | `pm-hse-view/src/framework/components/common/agentChat/index.vue` | 自主会话抽屉整体：阶段条 + 计划清单 + 问题卡片 + 思考组 + 流式行 + 终态结论卡 + 暂停/恢复/停止/评价；props `sessionId/title/renderers`，emits `finished/restart`；内部 2s 轮询 status+events |
| `AgentChatPanel` | `pm-hse-view/src/framework/components/common/agentChat/AgentChatPanel.vue` | 引擎无关通用聊天父组件（气泡/滚动/输入区/历史抽屉/评价内置）；历史抽屉两过滤维度：`agentCode`（空=跨 agent）与 `historyScope`（mine 默认只看本人，用户场景；all 跨发起人聚合，管理类页面，列表项带发起人，删除/评价仅本人记录） |
| `AgentStages` | `pm-hse-view/src/framework/components/common/agentStages/index.vue` | 阶段条（自主会话 stages 与 flow trace 两数据源通用）；可选过程日志区与停止按钮 |
| `AskPlanChecklist` | `agentChat/AskPlanChecklist.vue` | 计划清单独立渲染（问数票据链同构载体） |
| API 封装 | `pm-hse-view/src/framework/apis/agent/index.ts` | sessionStart/Stop/Pause/Resume/Answer/Rate/Active 等 |

接入示例（自主抽屉）：`pm-hse-view/src/framework/views/Insight/Agent/Admin/AgentAssetEdit.vue`——业务入口拿 sessionId 后 `<AgentChat :session-id="...">` 即完整可用；同文件 `reconnectAutonomous` 为 KEEP_RUNNING 场景的重连范式（进页查 `sessionActive` → 按 `subject` 命中 → notification 提示重连）。

---

## 五、配置项速查

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `llm.agent-session.key-prefix` | `llm:agent:session:` | 会话存储 Redis 键前缀 |
| `llm.agent-session.ttl-seconds` | `86400` | 会话快照/事件流 TTL |
| `llm.agent-task-gate.key-prefix` | `llm:agent:task-gate:` | 闸门 Redis 键前缀 |
| `llm.agent-task-gate.orphan-millis` | `90000` | 闸门失联阈值（锁内心跳超时即强删） |

实现自动装配（`AgentSessionAutoConfiguration`）：类路径有 `core/redis` → Redis 实现（分布式）；否则内存实现（单实例 fallback）。

---

## 六、参考实现索引

| 主题 | 文件 |
| --- | --- |
| 模板基类接入范式 | `llm/.../agent/AbstractToolLoopAgent.java` |
| 直接实现接口（桥接存量任务体） | `insight/.../flow/AssetGenAgentDefinition.java` |
| 直接实现接口（多轮问数） | `insight/.../flow/MaintainQueryAgentDefinition.java` |
| 约定名工具桥接范式（plan/finish 自查） | `insight/.../service/AssetGenAgentTools.java` |
| 闸门接入范式 | `insight/.../service/SmartAgentAssetGenerateService.java` |
| 会话层生命周期/终态收口 | `llm/.../agent/session/AgentSessionService.java` |
| 通用端点 | `llm/.../agent/AgentSessionController.java` |
| 闸门原语 | `llm/.../agent/gate/AgentTaskGate.java`（Redis/InMemory 两实现） |
