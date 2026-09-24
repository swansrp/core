# OpenHands agent-server 部署与对接（实测口径）

> 适用对象：`core/agent-runtime` 的 OpenHands provider（本模块）。
> 事实来源：omni（10.3.6.60，SSH 别名）上 `/data/openhands-spike/` 的**运行中实况**（2026-09-24 核对），
> 非官方文档转述。改动共享测试机上的这套服务**必须先经用户同意**。

## 1. 部署事实（当前实况）

| 项 | 值 |
|---|---|
| 主机 | omni（10.3.6.60，SSH 别名，root 免密），**共享测试机**（WeKnora/dbgpt 等在跑） |
| 目录 | `/data/openhands-spike/`（Dockerfile、probe 脚本、state/oh-home/work 三个挂载源） |
| 镜像 | `ghcr.io/openhands/agent-server:latest-python`（目录里的 Dockerfile 是早期本地构建尝试，**实际用的是 GHCR 镜像**） |
| 容器 | `openhands-spike`，`restart=no` |
| 端口 | 容器 `8000/tcp` → 宿主 `127.0.0.1:13100`（**只绑回环**，见 §5 安全） |
| 挂载 | `/data/openhands-spike/state:/.openhands-state`、`…/oh-home:/home/openhands/.openhands`、`…/work:/workspace` |
| SDK | 镜像内 `openhands-agent-server`（SDK v1.49.4 实测口径；`OPENHANDS_BUILD_GIT_REF=refs/heads/main`） |

## 2. 关键环境变量（容器 env，实测）

| 键 | 作用 |
|---|---|
| `LLM_MODEL` | 上游模型（LiteLLM 前缀写法，实测 `openai/qwen3.8-flash`） |
| `LLM_BASE_URL` | OpenAI 兼容端点（实测内网/云 MaaS compatible-mode/v1） |
| `LLM_API_KEY` | 模型密钥（**不进库、不进日志**；relay 侧取用见 §6） |
| `OH_SESSION_API_KEYS_0/1` | **会话级 API Key**：我方 relay 调它时的 `X-Session-API-Key` 凭据（REST 与 WS 同用） |
| `OH_SECRET_KEY` | 会话密钥加密种子 |

注意：`LLM_*` 是 **OpenHands 自己的模型提供方**（它内部跑 LLM 用），与我方 relay 的 `AGENT_RUNTIME_*` 是两回事。

## 3. 标准启动（重建/换机照此）

```bash
docker run -d --name openhands-spike \
  -p 127.0.0.1:13100:8000 \
  -v /data/openhands-spike/state:/.openhands-state \
  -v /data/openhands-spike/oh-home:/home/openhands/.openhands \
  -v /data/openhands-spike/work:/workspace \
  -e LLM_MODEL=openai/qwen3.8-flash \
  -e LLM_BASE_URL=<MaaS compatible-mode 端点> \
  -e LLM_API_KEY=<密钥> \
  -e OH_SESSION_API_KEYS_0=<会话密钥> \
  ghcr.io/openhands/agent-server:latest-python
```

- `-p 127.0.0.1:…` **必须保持只绑回环**：它"有准入无身份"（见 §5），绑 `0.0.0.0` 等于把无身份读写开放给全内网。
- 每会话一个独立工作目录：上游自建 `/workspace/<conversation_id>/`，我方无需 mkdir。
- 健康检查：`GET /api/agent-profiles`（带 `X-Session-API-Key`）返回 200 即服务可用。

## 4. 我方接入配置（relay 侧）

- 凭据：`server/.env`（gitignored）里 `AGENT_RUNTIME_PROVIDER=openhands`、
  `AGENT_RUNTIME_BASE_URL=http://127.0.0.1:13100`、`AGENT_RUNTIME_API_KEY=<OH_SESSION_API_KEY>`。
- 鉴权头：REST 与 WS 都走 **`X-Session-API-Key`**（不是 Bearer）——见 `OpenHandsClient`。
- 开发机联调：spike 只在 omni 回环上，本机需
  `ssh -N -L 13100:127.0.0.1:13100 omni`（隧道是开发期姿势；生产 relay 与上游内网直连）。
- 开关：`my.agent.runtime.enabled=true` + `provider=openhands`（框架模块**默认不装配**）。

## 5. OpenHands 侧功能配置（profile 与子 agent）

- **profile 是可写的**：`GET/POST /api/agent-profiles/{name}`（POST = 保存，返回 201）。
  当前 `default` 已开 `enable_sub_agents=true`（revision 1）。
- **子 agent 类型是文件式的**：放 `~/.openhands/agents/<name>.md`（容器内），frontmatter
  `name/description/tools/model`，正文即 system prompt；`POST /api/sub-agents` 只读发现
  （load_user/load_project/load_builtin 三个开关）。
- ⚠️ `register_agent()` **不是**暴露给模型的工具（实测模型没有它）；子 agent 的注册途径就是上面的文件。
- 已知行为（与 codec 映射相关，详见 §6）：`task` 工具的子 agent **不产生子会话**
  （`sub_conversation_ids=[]`），报告直接作为 `TaskObservation` 回来。

## 6. 已知行为与坑（codec 已按此实现，改codec前必读）

完整清单见决策文档《openhands-接入形态决策.md》§9 与 `AgentSessionStore`/codec 注释。要点：

1. **正常收口不发助手 MessageEvent**：最终答复在 `FinishAction`/`TaskObservation`，
   codec 已把两者映射为正文/容器输出。
2. `task` 工具（子 agent 委派）**不产生子会话**：报告作为父级工具输出回来；
   codec 把它映射成规范 `spawn_subagent` 容器步（`agent_code`=子类型、输出=报告），
   **不伪造子事件流**。
3. 事件检索 `events/search` **limit ≤ 100**（传 200 会报错返回空）。
4. Redis 事件流写入的"读失败≠空表"约束见 `RedisAgentSessionStore`（中断标志位会污染 Redisson）。

## 7. SPI 边界（哪些进 SPI、哪些不进）

- **进 SPI**：会话/轮次/取消/历史的契约（DTO=块模型）+ 规范事件词表（`RuntimeEvents`）+
  附件两能力位（预签名 / 代收转推）+ **子 agent 的只读发现**
  （`listSubAgents()`，供管理面展示"这家上游有哪些可委派类型"；OpenHands 实现走 `/api/sub-agents`）。
- **不进 SPI**：子 agent 的**注册/启停/改配**——那是各家 runtime 自己的管理面
  （OpenHands=profile API 与文件目录；agent-system=它自己的机制），框架只消费结果，
  不替它们定义一套注册协议。原因：注册协议各家差异极大且与部署强绑定，进 SPI 会把
  框架变成"最小公分母注册中心"，反而锁死各家的能力。
