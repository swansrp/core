# LLM Agent 循环治理策略

> 适用范围：所有经 `ToolAgentRunner` 驱动的多轮工具循环链路——问数维护（解析/维护/修复）、自主维护会话（B3）、资产生成。
> 设计原则：**框架层出机制（零业务耦合），业务层出策略（轮次/预算/钉住谁/纪律写什么）**。

---

## 一、背景：治理前的实测问题

以维护问数链为例（某题 25+ 轮、13 分钟未收敛）：

| 症状 | 根因 |
|---|---|
| 同一张表反复重查字段/指标明细 | 滑动窗口裁掉早期工具结果，模型"失忆"重复拉取 |
| run_sql 用表别名被拒后浪费整轮 | 工具硬约束只写在提示词里，模型每轮看不到（被裁） |
| 单轮思考 3 分钟仍不决策 | 无时间预算，轮次可无限叠加 |
| 穷尽式数据画像（全量分布/空值率普查） | 提示词缺探索克制纪律 |

---

## 二、已实现机制（框架层 `core/llm`）

### 1. 自管工具循环 + 轮次上限
- `AgentLoopOptions.maxRounds`：每轮 generate → 执行工具 → 回填，突破 langchain4j 内置 10 轮硬限。
- 位置：`ToolAgentRunner.run`。

### 2. 滑动窗口上下文管理（`memoryWindow`）
- 保留头部（system + 首轮 user）+ 最近 N 条；切点自动对齐到非工具结果消息，避免拆散「AI 工具调用↔结果」配对触发端点协议报错。
- 位置：`ToolAgentRunner.trimToolMemory`。

### 3. 重要消息对钉住（`pinnedTools`）
- 登记工具（如 askUser、目录检索四工具）的调用↔结果对被驱逐前提取到头部**永久保留**，关键事实免疫窗口裁剪。
- 位置：`ToolAgentRunner.pinnedBefore`。

### 4. 驱逐区段确定性压缩摘要（digest）
- 被驱逐的非钉住消息压缩为「工具名(参数摘要)→结果摘要」一行式归档插入头部，上限 3000 字符（超则保留最近部分）；**零 LLM 成本**。
- 效果：裁窗后模型仍知道探索过什么、结论是什么，避免重复探索与重复推导。
- 位置：`ToolAgentRunner.digestOf / trimDigest`。

### 5. 工具同参缓存（`cachedTools`）
- 同一会话内同名+同参调用直接返回缓存结果（失败结果不缓存），重复拉取的执行开销归零。
- 与钉住配合：钉住防"重发"，缓存兜底"重发仍发生时"的执行成本。
- 约束：**仅登记只读探索类工具**（提案/写库等副作用工具严禁登记）。
- 位置：`ToolAgentRunner.run` 工具执行段。

### 6. 会话总时间预算 + 收口（`budgetSeconds`）
- 每轮循环头检查；超预算不再发起新一轮，与触顶同走收口路径：摘除工具定义 + 下达"立即输出最终结论"指令，保证**任何情况下都有结论**。
- 位置：`ToolAgentRunner.run` 循环头与收口块。

### 7. 工具错误回填自纠
- 工具异常序列化为 `【工具调用失败】+原因` 回填模型自纠，绝不让单次工具失败击穿整个会话。

### 8. 并行工具调用
- 模型一轮发起多个工具请求时逐个执行并回填，提示词鼓励批量调用以压缩轮数。

### 9. 过程透明与中断
- `LoggingModel` 每轮推理/工具调用各推一条进度日志（参数/结果截断防膨胀）；支持用户停止/暂停钩子（`sink.shouldStop()`）。

### 10. 直连兜底
- 模型不支持工具调用或工具探索耗尽时，降级为无工具直连出答案。

### 11. 工具结果入场卸载 + 句柄回捞（`toolResultOffloadChars`，2026-09 新增）
- 超过阈值（字符）的工具结果**入场即替换**为「头尾预览 + `tool_call_id` 句柄」指针，不再等驱逐时才摘要——全文每轮重付的痛点从源头掐断；原文始终在会话事件流全文归档（呈现形态分层），模型经内建 `recallToolResult` 工具按句柄零损失取回。
- 三道闸门防失控：单 run 回捞次数上限（默认 3）、单次回捞返回上限（默认 20000 字）、回捞结果不再二次卸载。钉住工具 ∪ askUser ∪ recallToolResult 永不卸载；收口路径最后一次 generate 前追加的消息一律原文。
- 关闭态（`0`，框架默认）零行为变化；`cachedTools` 命中路径同样卸载并配本次新句柄。
- **句柄有两个来源**（`I15`）：① 入场卸载指针首行的 `tool_call_id=`；② 被窗口裁进【探索记录摘要】时，被驱逐的工具结果行尾追加的 `（句柄=…）`——原文恒在会话事件流（I4），故**被裁掉的结果与窗内指针同等可回捞**，不存在"裁切即失联"。二者共用同一把同源判据 `recallUsable = (卸载开启 || token 预算开启)`（A10 起不再叠通道位）：回捞工具是否进 specs、摘要是否写句柄，由这一个布尔决定，杜绝"有句柄没工具 / 有工具没句柄"的半开态。默认全关时 `recallUsable=false`，specs 不增工具、digest 逐字符不变（I9）。摘要头部随之多一行回捞指引，仅该形态存在。
- **回捞通道由框架两级自带**（`I16`，2026-09 修正案 A10）：取数**先走会话事件流**（`supportsToolResultRecall()=true` 的链路，跨 run、跨实例、重启后仍可捞），miss 或本无通道时落到 `RunScopedRecallBuffer`——run 作用域内暂存"模型已看不到的原文"（被卸载为指针的 + 被驱逐进摘要的），随本次 `run()` 结束释放。故任何链路零改动即享有卸载与回捞，运维开关一置正值就生效；`supportsToolResultRecall` 从此只决定**可回捞范围**（跨 run / 仅本 run），不再是卸载前置闸。I16 三条：①只存不可见者（窗内原文与收口保原文不入表）；②生命周期=一次 run，绝不跨 run 引用；③只读，绝不写事件流（不破 I13）。缓冲容量 `AGENT_TOOL_RECALL_BUFFER_CHARS`（默认 120000 字）按插入序 FIFO 挤出最旧句柄——长 run 累积的不可见原文可远超窗口，不设上限即无界堆占用；被挤出的句柄回捞返回"未找到（已被容量挤出）"，属显式记录的有界退化。裸链路若将来改成多 run 复用同一消息历史，**必须同时接通道**，否则上一 run 的句柄必失联。
- 位置：`ToolResultOffloader` / `AgentToolRecall` / `ToolAgentRunner.appendToolResultMessage`、摘要句柄 `ToolAgentRunner.digestOf`、回捞读源 `AgentSessionContext.loopListener().recallToolResult`（优先）与 `RunScopedRecallBuffer`（兜底，A10）。

### 12. Token 维度上下文预算（`contextTokenBudget`，2026-09 新增）
- 裁窗计量单位补上 token 量纲：正值时为「进入模型的消息估算 token 上限」（估算式 CJK×1.0 + 其他×0.3 + 每条 4 + 工具定义×1.2，方向只高不低，见 `ContextTokenEstimator`），与条数窗口**取更严者**；被钉住对不可驱逐导致保留段仍超预算时只告警（软超），不迭代重算。
- 驱逐出口唯一：token 触发与条数触发共用同一条 digest 归档路径，不新增摘要块、不新增 LLM 调用（`recallUsable` 时仅在被裁的工具结果**行尾**多挂 `（句柄=…）`，行结构不变，见 §二第 11 条 I15）。
- 治理开启态每次 generate 前输出一行「上下文计量」（条数/字符/估算 token/本轮卸载/累计卸载/累计回捞/累计驱逐），与 `LoggingModel` 透出的端点真实 token 形成估算 vs 实测校准回路。
- 关闭态（`0`，框架默认）行为与改造前逐条一致（有等价性回归测试锁死）。
- 位置：`AgentContextBudget`（三级取值）/ `ContextTokenEstimator` / `ToolAgentRunner.trimToolMemory`。

---

## 三、已实现策略（业务层 `core/insight`，SmartQueryMaintainService）

### 1. 三链分级配置（常量集中类头部，可调）

| 链路 | 轮次上限 | 窗口 | 时间预算 | 钉住 | 同参缓存 |
|---|---|---|---|---|---|
| 解析（自然语言→semantic_query） | 8 | 30 | 240s | 目录四工具 | 只读探索 8 工具 |
| 维护（探索+建议） | 14 | 60 | 420s | 目录四工具 | 只读探索 8 工具 |
| 修复（执行报错→修补） | 6 | 20 | 150s | 目录四工具 | 只读探索 8 工具 |
| 自主维护会话（B3） | 30 | 60 | —（用户交互式） | — | 只读探索 8 工具 |

- 钉住集 `CATALOG_PINNED_TOOLS`：searchAssets / describeEntity / metricDetail / dimensionDetail。
- 缓存集 `READONLY_CACHE_TOOLS`：上述四工具 + describeTable / sampleRows / groupByField / runSql。

### 2. 资产目录按需供给
- 提示词常驻**名称级精简索引**（每资产一行，2000 行截断）；明细经 `SemanticCatalogTools` 四工具按需拉取。
- 旧草稿 `{catalog_json}` 占位符经 `injectIndex` 兼容同口径。

### 3. 提示词探索纪律（TOOL_MODE_SUFFIX，用户提示词头部永不被裁）
- 探索克制：同一事实最多探索两轮，信息足够立即输出最终 JSON，禁止穷尽式数据画像。
- 决断：同一问题只权衡一次，多方案选最贴近规则者直接执行，禁止跨轮反复权衡。

### 4. 工具自描述（规则随工具每轮可见）
- runSql `@Tool` description 内嵌硬约束：db.tbl 全名、**禁止表别名**、只读、50 行上限。
- 领域规则进工具描述的好处：工具定义每轮随请求发送，天然免疫窗口裁剪，比提示词后缀更稳。

### 5. 引擎硬规则沉淀进维护提示词
- expression 仅支持 `db.tbl.col` 三段式直引（函数包裹必失败）；年份口径优先复用已有年份维度（如 dy）；
- 带 dd 快照分区的 ODS 表探索时自行附加 `dd = 最大 dd`；产出资产时信任既有实体缺省过滤口径。

---

## 四、业界最佳实践对照

| 本项目机制 | 业界对应 |
|---|---|
| 轮次上限 | LangGraph `recursion_limit`、OpenAI Agents SDK `max_turns`、AutoGen `max_rounds` |
| 总时间预算 + 收口 | 生产级 guardrail（轮/token/时间三维停止条件）；"预算耗尽也要产出最终答案"标准降级 |
| 滑动窗口裁旧工具结果 | Anthropic context editing、LangGraph `trim_messages`、MemGPT 分层记忆 |
| 钉住重要消息对 | Anthropic context engineering "structured note-taking / scratchpad" |
| 驱逐摘要（确定性） | 上下文摘要压缩的轻量实现（无 LLM 成本变体） |
| 同参缓存 | 工具调用 memoization（只读工具安全） |
| 错误回填自纠 | 各框架共识做法 |
| 并行工具调用 | OpenAI/Anthropic parallel function calling |
| 探索纪律提示词 | Agent prompting 指南常规项（明确停止标准、防无限探索） |

---

## 五、规划中（未实现）

| 项 | 说明 | 优先级 |
|---|---|---|
| LLM 摘要压缩 | 现摘要为确定性一行式；复杂场景可引入小模型对驱逐区段做语义摘要（多一次 LLM 调用，需权衡延迟） | 低 |
| 模型分档 | 解析/修复换快速非推理模型、维护用深度模型，压缩单轮延迟（需业务确认） | 中 |
| 链路耗时可观测 | 每链轮数/耗时/工具调用数打点，支撑预算调优的数据依据 | 中 |

---

## 六、调优指南

- 所有预算/轮次/窗口常量集中在 `SmartQueryMaintainService` 类头部（`*_BUDGET_SECONDS` / `MAX_*_ROUNDS` / `*_MEMORY_WINDOW`），按体感直接改。
- 观察收口日志："工具探索达上限…" = 轮次触顶；"会话总耗时达预算…" = 时间触顶——两者都保证有最终结论。
- 若某工具重复调用仍频繁：确认其已登记进 `CATALOG_PINNED_TOOLS`（防重发）与 `READONLY_CACHE_TOOLS`（零成本兜底）。
- 新增硬约束时的落点顺序：①工具 `@Tool` description（每轮可见）> ②用户提示词头部（永不被裁）> ③中段提示词（会被裁，避免）。
- L1 预算治理的运维灰度开关（sys_config，均可被 `AgentLoopOptions` 正数值覆盖；**两类键语义不同**——开关键 `AGENT_TOOL_RESULT_OFFLOAD_CHARS` / `AGENT_CONTEXT_TOKEN_BUDGET` 默认 `0`，0 即"关闭"设计本体；闸门键默认即保守值（1000/3/20000），DB 填 `0` 或负值一律**回落保守默认**而非关闭，要整体关闭请关总开关 `AGENT_TOOL_RESULT_OFFLOAD_CHARS=0`。未显式开启的链路行为与改造前逐条一致）：
  `AGENT_TOOL_RESULT_OFFLOAD_CHARS`（卸载阈值字，建议 4000）、`AGENT_TOOL_RESULT_PREVIEW_CHARS`（指针预览字，默认 1000）、`AGENT_CONTEXT_TOKEN_BUDGET`（token 预算，建议 24000）、`AGENT_TOOL_RECALL_MAX_PER_RUN`（回捞次数上限，默认 3）、`AGENT_TOOL_RECALL_MAX_CHARS`（回捞返回上限字，默认 20000）、`AGENT_TOOL_RECALL_BUFFER_CHARS`（run 内回捞缓冲容量字，默认 120000，A10）。
- 判断治理是否真生效：看过程日志「上下文计量」行与「token 预算触发驱逐」「工具结果入场卸载」；关闭态不产生任何计量行（事件流零新增）。
