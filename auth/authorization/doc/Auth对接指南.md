# Auth 对接指南（core/authorization）

> 适用对象：以本 core 为底座的部署形态（墨枢 bidr 形态、plsintec 形态、子系统接入形态）。
> 本文回答两件事：**① 多租户怎么建、SSO 用户的租户归属怎么落；② 本系统作为 IdP 时，子系统怎么接 SSO。**
>
> 文档分工：
>
> | 文档 | 内容 |
> |------|------|
> | **本文** | 多租户 + SSO 总览、协议契约、安全纪律、故障排查 |
> | [子系统SSO对接指南.md](子系统SSO对接指南.md) | 子系统接入四步的完整操作手册（partner 渠道 → 公钥 → 私钥注入 → 子系统配置 → 联调验收） |
> | [SSO接入指南.md](SSO接入指南.md) | `SsoUserHandler` SPI 与 `POST /web/sso/login` 保留通道的深潜 |
>
> 文中事实以当前源码为准（2026-11-04 快照）。**已退役协议不再提供回滚窗口**，见 §3.7。

---

## 一、能力地图与通道选型

### 1.1 四条通道

| 通道 | 入口 | 鉴权方式 | 典型使用方 |
|------|------|----------|-----------|
| 本地账号登录 | `POST /web/auth/login`（bidr 形态）/ `POST /web/login` | 账号 + 密码 | 墨枢自身用户、未接 SSO 的部署 |
| **OAuth2 授权码（子系统 SSO 推荐）** | `GET/POST /web/oauth2/authorize` → `POST /web/oauth2/token` | InkHub 登录态 + client_id/client_secret | 所有新接入的子系统 |
| SPI 登录口（保留形态） | `POST /web/sso/login` | `SsoUserHandler` 线程态 | bidr 形态（idm）、自接其它 IdP 的部署 |
| 机器/数据面 open-api | `/open-api/v1/identity/snapshot`、`/open-api/v1/projects`、`/open-api/v1/tenants` | `x-api-key` 或 Bearer access_token | 子系统拉身份/租户/项目镜像 |

### 1.2 子系统接 SSO 该选哪条

- **新接入一律走 OAuth2 授权码 + JWKS**（§三）。它是当前唯一在维护的子系统 SSO 协议，公钥可轮换、接入方零密钥。
- `POST /web/sso/login` 只用于"身份已经在当前请求线程里"的形态（bidr 形态的 idm 验签、子系统自接 IdP）。它不解决"浏览器从墨枢跳过来"的问题，不要拿它当子系统 SSO 主链。
- `/open-api/v1/*` 是**数据面**，不是登录面：它给子系统拉身份快照、租户清单、项目镜像用，不签发登录态。

---

## 二、多租户

### 2.1 先给结论：租户可以 seed 出来，不必先手工配

**可以 seed。** 在部署档 yml 里写：

```yaml
identity:
  tenant:
    code: EPC1101          # 租户业务编码（ac_tenant.code，不是 id）
    name: EPC联调租户-1101
    auto-create: true      # 空库引导：无该 code 自动建行；已有则幂等跳过
```

`TenantSeeder`（`@Order(LOWEST_PRECEDENCE - 3)`）在启动时执行：`ac_tenant` 里没有该 `code` 的有效行就建一行（`valid='1'`，`name` 空白时回退用 `code`），已有就幂等跳过。重启不会重复建，也不会改已有行。

要"必须预先建好、配错就拒绝启动"的严格形态，则：先建租户（Portal 管理面或 SQL），再把 `auto-create` 设为 `false`——此时 `identity.tenant.code` 有值但库里没有有效行，应用直接抛 `IllegalStateException` 拒绝启动。

### 2.2 两种建租户方式

| 方式 | 配置 | 适用场景 | 行为 |
|------|------|----------|------|
| **A. 配置即种子（推荐）** | `identity.tenant.code/name` + `auto-create: true` | dev / pre / 空库引导 / 单租户交付 | 启动时缺行则建；已有则跳过；重启幂等 |
| **B. 预建 + 严格闸** | 先建 `ac_tenant` 行；`identity.tenant.code` + `auto-create: false` | 生产环境、租户由运维/Portal 统一管理 | 缺行即拒绝启动（fail-fast）；不会静默半启动 |

> 🔴 两种方式都只保证**租户目录**（`ac_tenant`）里有一行；它们**不**替用户建归属关系。用户的租户归属在 `ac_user_tenant`，见 §2.4。

**手工预建的入口**（`AcTenantPortalController`，类级 `AuthAdmin`）：

```text
GET  /web/tenant/portal/list                 租户目录（有效租户）
GET  /web/tenant/portal/detail?id=            租户详情
POST /web/tenant/portal/create?name=&code=    新建（code 缺省自动生成 8 位随机码）
POST /web/tenant/portal/update?id=&name=&code= 修改
POST /web/tenant/portal/disable?id=           停用（软删）
```

也可以用 SQL 直接插 `ac_tenant`（`code` 索引故意非唯一，撤销后再启用同 code 不撞键；停用＝`valid='0'`，再启用＝插新行）：

```sql
INSERT INTO ac_tenant (name, code, create_by, create_at, update_by, update_at, valid)
VALUES ('EPC联调租户-1101', 'EPC1101', 'ops', NOW(), 'ops', NOW(), '1');
```

### 2.3 `TenantSeeder` 的三种落库形态

| `identity.tenant.code` | `auto-create` | 库里有无有效行 | 启动行为 |
|------------------------|---------------|----------------|----------|
| 空白 | 任意 | — | 跳过并 `log.info`（非租户形态） |
| 有值 | `true` | 无 | 建行（`name` 空则回退 `code`，`valid='1'`，SYSTEM_USER 审计） |
| 有值 | `true` | 有 | 幂等跳过 |
| 有值 | `false` | 无 | **抛异常拒绝启动**（配置点名了租户而库里没有＝装配错误） |
| 有值 | `false` | 有 | 幂等跳过 |

### 2.4 数据面：`ac_tenant` 与 `ac_user_tenant`

- `ac_tenant`：租户目录。`code` 是对外可读业务编码（如 `EPC1101`），`valid` 是软删位。
- `ac_user_tenant`：用户 ↔ 租户归属关系。一个用户可有多行（多租户成员），每行带 `is_admin`（是否该租户管理员）。
- **`identity.tenant.code` 是"账号创建时的默认赋籍租户"，不是"用户唯一允许的租户"**：
  - 建号（MDM/飞书同步、注册、`loginOrReg`）时，`CreateUserServiceImpl` 按 `TenantResolver` 解析出默认租户 code，查 `ac_tenant` 有效行，再 `insertIfAbsent(userId, tenantId, false)` 落一行 `ac_user_tenant`。
  - 用户后续可以被加入其它租户（`ac_user_tenant` 多行），登录后通过租户切换面切换（§2.8）。
  - 平台档/系统管理级账号可以零绑定（旁路），普通账号零归属在 tenant 模式下会被拒登或查不到数据。
- **open 模式例外**：`my.auth.register-mode=open` 时，建号**不**赋默认租户、不写 `ac_user_tenant`（全局账号形态，plsintec 档）。

### 2.5 三档注册模式（`my.auth.register-mode`）

**只认 yml，不入 `sys_config`、参数页不可热改；切换模式＝改 yml + 重启。**

| 模式 | 语义 | 建号/注册 | SSO 首登 | 数据面 |
|------|------|-----------|----------|--------|
| `open` | 全局账号形态 | 不绑租户 | 历史行为：可自动建号 | 不挂租户、闸旁路（plsintec 形态） |
| `tenant` | 多租户形态（墨枢常态） | 必须带有效租户码；建号按 `identity.tenant.code` 赋籍 | **不自动建号**；已有账号零归属拒登 | 零归属查不到数据；`my.tenant.line-enabled` 开启时登录补绑钩子生效 |
| `closed` | 关闭自助注册 | 拒绝一切自助注册 | 不自动建号 | 存量账号照常登录（租户就绪闸只在 `tenant` 模式生效） |

关键实现点（`LoginServiceImpl`）：

- `assertSsoAutoCreateAllowed()`：`tenant` 模式拒绝 SSO 自动建号（文案："当前为租户模式，账号需由管理员/主系统创建并分配租户后使用"）；`closed` 模式拒绝自助注册。
- `assertSsoTenantReady(user)`：仅 `tenant` 模式生效；`user_type` 为 `SSO` 或 `REGISTER` 的账号必须至少有一行 `ac_user_tenant`，否则拒登（"该账号尚未分配租户，请联系管理员分配后重新登录"）；`SYSTEM`/`ACCOUNT` 系统管理级旁路。
- `assertRegisterAllowed(tenantCode)`：`tenant` 模式必填租户码且命中有效租户；`open` 模式选填，给了也校验并挂。

### 2.6 SSO 用户的租户归属（本文的核心问题）

**结论：tenant 模式下，SSO 用户必须至少有一行 `ac_user_tenant`；`identity.tenant.code` 必须配成一个真实存在的租户（seed 或预建），否则建号和补绑都会失败。**

分通道说清楚：

#### A. SPI 登录口 `/web/sso/login`（§3.6）

`tenant` 模式下有三条硬规则：

1. **首登不建号**：`ac_user` 查不到且 `ac_account` 也查不到时，`assertSsoAutoCreateAllowed()` 直接拒绝。
2. **主数据可建号**：`ac_user` 查不到但 `ac_account` 主数据命中时，`loginOrReg` 建号并按默认租户赋籍——这条是合法的 SSO 首登路径。
3. **存量零归属拒登**：`ac_user` 已存在但 `ac_user_tenant` 为空，`assertSsoTenantReady()` 拒绝。

另外，`my.tenant.line-enabled=true` 时，`TenantBindOnLoginService` 会在登录时给"零归属且非平台档"的存量账号补绑默认租户（`is_admin=0`），这是存量主数据账号逐步补籍的钩子。平台档账号（含 admin 角色）保持零绑定旁路，不会被绑死。

#### B. OAuth2 授权码通道（§3.2）

🔴 **这条通道本身不替子系统做"零租户拒登"判定。** `authorize` 只要求：
- 调用方已登录 InkHub（header 或 `INKHUB_TOKEN` cookie）；
- `client_id` 在 `ac_partner` 登记；
- `redirect_uri` 在该 partner 的 `ac_sso_redirect` 白名单里全串命中；**本地/内网回调免登记**
  （host＝`localhost`／`127.0.0.0/8`／`10.0.0.0/8`／`::1`，scheme 仅 http(s)，端口/路径任意 ⇒ 直接放行）。

它会把用户当前的全部租户 id 放进 access_token 的 `tenant_ids` claim（可能为空数组）。**"零租户能不能进子系统"由子系统侧判**：

- 用 core 共享编排（`authorization-client` 的 `SubsystemSessionService`，推荐）＝**内置闸**：非管理员空租户集直接拒绝；空集用户回主系统 `/authz/check`（`scope=""`＋`permission="admin"`）实时判平台管理员，判是才放行（管理员设计上不归属租户、可切换各租户）。判据不看 uid 数值。
- 自建会话链的系统必须自己 fail-closed，不能把空 `tenant_ids` 当"没限制"（见 [子系统SSO对接指南.md](子系统SSO对接指南.md) §4.6）。

所以对接方要保证：
- 主系统侧 `identity.tenant.code` 配好（否则该用户可能根本建不出来/补不上籍）；
- 该 SSO 用户在主系统 `ac_user_tenant` 至少有目标租户一行（平台管理员除外）；
- 子系统侧开启 `my.authz.client.enabled=true` 并配好 partner 凭证，否则零租户回判恒 false，管理员也进不来。

#### C. `open` 模式

`open` 模式是全局账号形态：不绑租户、不写 `ac_user_tenant`、SSO 相关租户闸整体不适用。plsintec 档刻意不写 `my.auth.register-mode`（缺省即 open），也不声明 `identity.tenant.*`。**如果你的部署是 open 形态，"必须配归属租户号"这条不成立**——但也就没有多租户隔离。

### 2.7 `my.tenant.line-enabled` 到底管什么

它是 core 的**行级隔离开关**（`TenantLineIsolationConfiguration`）+ 登录补绑钩子的总开关。

- 开启后，实现了 `TenantIsolated` 标记的实体查询会被拼租户条件；InkHub 的业务实体当前**没有**实现 `TenantIsolated`（全仓 grep 0），所以在本仓里它的实际可观察作用主要是：
  1. `TenantBindOnLoginService` 登录补绑（零归属存量账号 → 默认租户，`is_admin=0`）；
  2. 建号时解析不到租户 ⇒ **fail-closed 拒建**（`CreateUserServiceImpl.resolveCreateTenantTarget`）。
- 关闭时：补绑钩子不生效、建号解析不到租户也不拒（返回 null＝不绑），但 tenant 模式的 SSO 零归属拒登仍然生效（那条闸不依赖 `line-enabled`）。

🔴 **不要在没有声明 `identity.tenant.*` 的档位随手打开 `line-enabled`**：建号会因解析不到租户而 fail-closed。prod 档当前就是这个原因没有打开（见 §2.10）。

### 2.8 租户切换与租户管理面

**登录态 UX 面**（`TenantMyController`，`@Auth(AuthLogin.class)`）：

| 端点 | 作用 |
|------|------|
| `GET /web/tenant/my/list` | 我的租户清单。平台档/系统管理级＝全部有效租户；普通成员＝成员关系 ∩ 有效租户（停用租户不展示） |
| `POST /web/tenant/my/switch?tenantId=` | 切换当前租户：改写当前 token 的 `TENANT_ID`（提权档连 `TENANT_IDS` 一并改写），免重登录；下一请求重建线程态 |

**租户管理面**（`AcTenantPortalController`，平台档 `AuthAdmin`；成员管理按 `ac_user_tenant.is_admin` 或平台档判权）：

| 端点 | 作用 |
|------|------|
| `/web/tenant/portal/list`、`/detail`、`/create`、`/update`、`/disable` | 租户目录维护 |
| `/web/tenant/portal/my/members?tenantId=` | 本租户成员清单 |
| `/web/tenant/portal/my/member/add?tenantId=&userId=&isAdmin=` | 加成员 |
| `/web/tenant/portal/my/member/remove?tenantId=&userId=` | 移成员（物理删关系行） |
| `/web/tenant/portal/my/member/admin?tenantId=&userId=&isAdmin=` | 切换成员 `is_admin` |

### 2.9 存量数据回填（InkHub 特有）

已有空间树、但 `ac_user_tenant` 为空的存量库，可用 `server/tools/tenant-backfill.js` 一次性回填：

- 默认 `--dry` 只读预演；`--apply` 单事务写库。
- 分组判据：`kb_space.node_path` 首段 = 租户（`ac_tenant.code = kbroot-<根id>`）。
- 成员经 `ac_user.customer_number` 换 `user_id` 落 `ac_user_tenant`；root 空间 role ∈ {admin, owner} ⇒ `is_admin=1`。
- `personal` 空间与未挂树的节点恒为 NULL（不绑租户）。
- 🔴 写库前必须人工复核 dry 报告；这是数据修改动作，按项目纪律需人工确认。

### 2.10 上线前缺口：prod 档尚未声明 `identity.tenant.*`

`server/inkhub-common/src/main/resources/config/application-prod.yml` 当前：

- `my.auth.register-mode: 'tenant'`（多租户形态）；
- **未声明 `identity.tenant.*`**；
- **未开 `my.tenant.line-enabled`**（避免未声明租户时建号 fail-closed）。

上线前必须补租户正源（环境变量 `IDENTITY_TENANT_CODE` / `IDENTITY_TENANT_NAME`，或 yml），否则 tenant 模式下新号不赋籍、数据闸照拦。补好之后再按 dev/pre 的口径决定是否打开 `line-enabled`。

---

## 三、SSO（本系统作为 IdP）

### 3.1 主链：OAuth2 授权码 + JWKS

```text
浏览器                 子系统前端                     墨枢（IdP）                      子系统后端
  │  访问子系统            │                              │                              │
  │──────────────────────>│  未登录                       │                              │
  │                       │  location.href =             │                              │
  │                       │  VITE_ssoLoginUrl + state    │                              │
  │                       │─────────────────────────────>│  /web/oauth2/authorize       │
  │                       │                              │  查登录态（header/cookie）    │
  │                       │                              │  查 client/redirect 白名单    │
  │                       │  302 redirect_uri?code&state │                              │
  │                       │<─────────────────────────────│                              │
  │                       │  POST /web/<app>/oauth2/exchange {code}                     │
  │                       │────────────────────────────────────────────────────────────>│
  │                       │                              │  POST /web/oauth2/token      │
  │                       │                              │<─────────────────────────────│
  │                       │                              │  RS256 JWT access_token      │
  │                       │                              │─────────────────────────────>│
  │                       │                              │  本地验签（JWKS 公钥）        │
  │                       │                              │  租户镜像 + 本系统会话        │
  │                       │  本系统 token                │                              │
  │                       │<────────────────────────────────────────────────────────────│
  │  回到原页面            │                              │                              │
```

关键点：

- 墨枢**只签票**，不替子系统建会话；子系统必须自己换会话、自己控权限。
- 墨枢的 `authorize` 需要**浏览器登录态**：整页导航带不上 SPA 存在 localStorage 里的 `Authorization` 头，所以端点额外接受 `INKHUB_TOKEN` cookie 回退（header 优先）。这是 authorize 独有的回退，其他端点不适用。
- `redirect_uri` 白名单是**全串精确匹配**（trim 后等值），不是 origin 匹配、大小写敏感；
  **例外＝本地/内网回调免登记**（`localhost`／`127.0.0.0/8`／`10.0.0.0/8`／`::1`，http(s)、端口任意，
  见 §3.7）——这是 OAuth2 通道自己的规则，与已退役 ticket 通道的 origin 等值口径不同。

### 3.2 三个端点

#### 3.2.1 `GET/POST /web/oauth2/authorize`

| 项 | 说明 |
|----|------|
| 入参 | `response_type=code`、`client_id`、`redirect_uri`、`state`（可选）、`scope`（可选） |
| 登录态 | 先抽 `Authorization` 头（`AuthTokenUtil.extractToken`），无头时回退 `INKHUB_TOKEN` cookie；用 `TokenService.verifyToken` 核验 |
| 未登录 | 302 到 `{context-path}/#/login?redirect_uri=<原 authorize 地址去掉前导斜杠>`（前缀随 context-path，如 `/inkhub/#/login`）；登录页登录后 replace 回跳 |
| client/redirect 未登记 | **就地 400 纯文本**，不跳转（未核验的 redirect_uri 不得作为跳转目标） |
| `response_type≠code` | 302 回已核验的 `redirect_uri`，带 `error=unsupported_response_type`（可带 `state`） |
| 成功 | 签发单次有效 code（默认 300 秒），302 回 `redirect_uri?code=...&state=...` |
| 绑定上下文 | `client_id`、`redirect_uri`、`user_id`、`customer_number`、`user_name`、`name`、`tenant_ids`、`scope` 随 code 存 Redis |

🔴 `redirect_uri` 在 authorize 和 token 两处必须**同串**；token 端点还会按 partner 的当前白名单**再闸一次**（防"登记但停用"或白名单变动）。

#### 3.2.2 `POST /web/oauth2/token`

form 形态（`application/x-www-form-urlencoded`，secret 走 body，不走 Basic 头）：

```text
grant_type=authorization_code
code=<authorize 回跳带回的 code>
client_id=<ac_partner.app_key>
client_secret=<ac_partner.app_secret>
redirect_uri=<与 authorize 时完全一致的全串>
```

成功（HTTP 200）：

```json
{
  "status": { "code": 0 },
  "payload": {
    "access_token": "<RS256 JWT>",
    "token_type": "Bearer",
    "expires_in": 3600
  }
}
```

> `OAuth2Client` 同时兼容"统一信封 `{status,payload}`"和"裸 `{access_token,...}`"两种形态；手写客户端请按 `payload` 优先解析。
> `scope` 若在 authorize 时传了且非空，会原样出现在响应里。

失败（HTTP 400，标准 OAuth2 错误体）：

```json
{ "error": "invalid_client", "error_description": "client_secret 校验失败，拒绝授权" }
```

错误码取值：

| `error` | 触发点 |
|---------|--------|
| `unsupported_grant_type` | `grant_type` 不是 `authorization_code` |
| `invalid_request` | `code` 或 `client_id` 缺失 |
| `invalid_client` | `client_id` 未登记 / `client_secret` 不匹配（恒时比较） |
| `invalid_grant` | code 无效/过期/已使用；code 与 client_id 不匹配；redirect_uri 与签发时不一致；redirect_uri 已不在白名单 |
| `server_error` | `identity.idp` 签发钥匙未配置 |

**授权码语义**：单次有效（Redis `DEL` 原子裁决消费权，并发双消费恰一成功）、默认 300 秒过期、`SecureRandom` 32 字节 base64url（约 256 bit 熵）。

#### 3.2.3 `GET /web/oauth2/jwks.json`

- **公开端点，无需任何鉴权。**
- 返回：

```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "<base64url 模数>",
      "e": "<base64url 指数>",
      "kid": "<公钥指纹>",
      "use": "sig",
      "alg": "RS256"
    }
  ]
}
```

- `kid` = 公钥 X.509 DER 编码的 SHA-256 摘要前 8 字节 hex（16 字符），与 JWT header 的 `kid` 同一真相。
- 私钥未配置 ⇒ HTTP 503 纯文本（端点保持关闭）；启动只 `log.warn`，不阻断上下文。

### 3.3 密钥体系

| 项 | 口径 |
|----|------|
| 算法 | RSA 2048，RS256（`SHA256withRSA`） |
| 私钥 | PKCS#8 DER 的 base64，放 `identity.idp.private-key`（部署侧从 `IDENTITY_IDP_PRIVATE_KEY` 环境变量注入） |
| 公钥 | X.509 DER 的 base64，放 `identity.idp.public-key`（可选；未配置时从 PKCS#8 私钥现场派生） |
| `kid` | 公钥 X.509 DER 的 SHA-256 前 8 字节 hex |
| token TTL | `identity.idp.token-ttl-seconds`，默认 3600 秒 |
| code TTL | `identity.idp.code-ttl-seconds`，默认 300 秒 |
| 未配置私钥 | authorize 仍可签发 code（不签票）；token 端点 400 `server_error`；JWKS 503；启动只 warn |
| 公钥发布 | `GET /web/oauth2/jwks.json`（公开）；接入方运行时拉取，不需要手工分发 |
| 密钥生成 | `project-setup` skill 的 `--gen-idp-keypair`（详见 [子系统SSO对接指南.md](子系统SSO对接指南.md) §三） |
| 密钥轮换 | 换 `server/.env` 两键 + 重启；旧 token 全部失效（全员重登）；接入方无需改配置，等 JWKS 缓存过期（默认 1 小时）或重启 |

### 3.4 access_token 契约

claims（`IdpJwtUtil`）：

| claim | 含义 | 消费方用途 |
|-------|------|-----------|
| `iss` | 固定 `inkhub` | 与其它 RS256 票源隔离（`OAuth2BearerToken` 硬校验；`IdpTokenVerifier` 在场时校验） |
| `sub` | 工号 `customer_number` | 账号对齐辅助键 |
| `uid` | 主系统 `ac_user.user_id` | 审计/身份标识（**平台管理员判定走 `/authz/check`，不看 uid 数值**） |
| `username` | 登录名 `ac_user.user_name` | **跨系统账号对齐的唯一稳定键** |
| `name` | 展示名 | 界面回显 |
| `tenant_ids` | 当前用户绑定的租户 id 数组（可能为空） | 子系统租户镜像/准入判定 |
| `app_id` | `client_id`（`ac_partner.app_key`） | **受众判据**：验票侧必须与本子系统 client_id 比对，缺失或不符一律拒 |
| `iat` | 签发时刻（epoch 秒） | — |
| `exp` | 过期时刻（epoch 秒） | 验签侧自判过期 |

- 头部：`{"alg":"RS256","typ":"JWT","kid":"..."}`。
- **没有 `refresh_token`**（P2 未做）。子系统会话过期后重新走 authorize 即可（墨枢登录态还在，通常免输密码）。
- 验签侧必须：验签名 + 自判 `exp`（含 30 秒时钟偏移容忍）+ `username` 非空；`iss` 在场时必须等于 `inkhub`；**`app_id` 必须等于本子系统 client_id**。`IdpTokenVerifier` / `IdpBearerVerifier` 已封装这些语义。
- 🔴 **为什么必须校验 `app_id`**：墨枢用同一把私钥给所有接入方签票。只验签不验受众时，"签给 epc 的票"会被另一个子系统接受——即跨子系统票混淆。受众值优先取 `my.idp.bearer.app-id`，缺省自动回退同宿主的 `my.oauth2.client.client-id`（两者是同一个 `ac_partner.app_key`）；两者都缺而验签腿开启 ⇒ **启动失败**，不允许静默放行。

### 3.5 子系统侧验票件（`authorization-client`）

子系统的后端依赖 `authorization-client` 模块即可获得：

| 类 | 作用 |
|----|------|
| `OAuth2Client` | `buildAuthorizeUrl(state)` 拼授权入口；`exchangeCode(code, redirectUri)` 调 token 端点换票 |
| `IdpTokenVerifier` | 本地验 access_token → `IdpPrincipal{username, customerNumber, uid, name}`；含 `iss`（在场时校验）与 `app_id` 受众闸；任何失败回 `null`（fail-closed，不抛） |
| `IdpBearerVerifier` | 验 `Authorization: Bearer <token>` → claims map；含 `app_id` 受众闸；供"子系统 API 直接用墨枢票"的形态 |
| `JwksKeyProvider` | JWKS 取钥：内存缓存 1 小时、单飞刷新、上次成功快照兜底、静态公钥兜底；**启动不依赖墨枢在线** |
| `AuthzCheckApi` | 调主系统 `AuthzCheckApi#check(userId, scope, permission)` / `authzVersion()` / `menus(userId)`；`scope=""`＋`permission="admin"` 即"是否平台管理员"（零租户回判用） |
| `SubsystemSessionService` | **子系统自签会话的共享编排**：镜像闸 → 租户闸 → 零租户回 `/authz/check` 判管理员 → 双票签发＋`TokenHolder` 收尾。仅在宿主带 `identity-mirror` 时由 `SubsystemSessionAutoConfiguration` 注册（主系统无镜像 ⇒ 自动退位） |

自动装配开关（三个 `enabled` 相互独立；完整登录链＝换票＋验票＋零租户回判）：

```yaml
my:
  oauth2:
    client:
      enabled: true
      base-url: http://<墨枢根地址>/<ctx>
      client-id: <ac_partner.app_key>
      client-secret: <ac_partner.app_secret>
      redirect-uri: <与白名单登记全串一致>
  idp:
    bearer:
      enabled: true
      server-base-url: http://<墨枢根地址>/<ctx>   # 公钥运行时从 /web/oauth2/jwks.json 拉
      # app-id: <ac_partner.app_key>                # 受众闸；可省略——缺省自动取 my.oauth2.client.client-id
      # public-key-base64: <X.509 公钥 base64>      # 仅离线/排查时使用；两种来源都缺且 enabled=true ⇒ 启动 fail-fast
  authz:
    client:
      enabled: true
      server-base-url: http://<墨枢根地址>/<ctx>   # /authz/check 基址（含 context path）
      app-key: <ac_partner.app_key>
      app-secret: <ac_partner.app_secret>
      fail-mode: CLOSED                             # 只作用于 RemoteAuthzChecker 缓存链；会话链每次实时调
```

> 🔴 `my.idp.bearer.enabled=true` 但既无 `server-base-url` 也无 `public-key-base64` ⇒ 启动直接失败（`IdpBearerAutoConfiguration.assertKeySourceConfigured`）。这是刻意把"静默 401"挡在启动期。
>
> 🔴 `my.idp.bearer.enabled=true` 但解析不出期望接入方（`my.idp.bearer.app-id` 与 `my.oauth2.client.client-id` 都为空）⇒ 启动直接失败（`assertAudienceConfigured`）。这是刻意把"跨子系统票混淆"挡在启动期，而不是运行期静默全部放行。
>
> 🔴 `my.authz.client.enabled` 缺省 false：不开就没有 `AuthzCheckApi`，共享编排的零租户回判恒 false ⇒ 零租户用户（含平台管理员）全被拒。这是 fail-closed 不是 bug。
>
> 🔴 用 `SubsystemSessionService` 的子系统需要**两行依赖**：`authorization-client` + `identity-mirror`（client 对镜像是 optional，不传递）。只引 client 时共享编排不装配。

### 3.6 SPI 登录口 `/web/sso/login`（保留形态）

| 项 | 说明 |
|----|------|
| 端点 | `POST /web/sso/login`，`@Auth(AuthNone.class)` |
| 入参 | `id_token`（形参，仅与既有契约形状兼容；真实身份不取它） |
| 身份来源 | `SsoUserHandler.resolveUserName()` 的线程态（bidr 形态＝idm 验签后的线程态；自接 IdP＝自写实现） |
| 出参 | `LoginRes`（与 `/web/login` 同形：`customerNumber`、`name`、`avtar`、`accessToken`、`refreshToken`） |
| 收尾 | `LoginFillTokenInf` SPI 链 + 登录留痕日志（与框架 `LoginController.afterLogin` 等价） |

三分支（`SsoLoginServiceImpl.issueFor`）：

1. `ac_user` 命中 → `assertSsoTenantReady(acUser)` → `login(acUser)`；
2. `ac_user` 未命中、`ac_account` 主数据命中 → `loginOrReg(account)` → 复核 `assertSsoTenantReady`；
3. 均无 → `createUser(userName, MD5(userName))` 并置 `UserTypeDict.SSO` 后 `login` —— **tenant/closed 模式下这条被 `assertSsoAutoCreateAllowed()` 拒绝**（§2.5）。

拒绝侧（四态互斥，文案即根因）：

| 态 | 触发 | 文案要点 |
|----|------|----------|
| 未接入 | 无 `SsoUserHandler` bean | "未接入SSO身份源（SsoUserHandler 实现未注册）" |
| 解析不出 | handler 在但返回 null/空白 | "SSO身份源未返回用户身份（id_token 缺失或未通过上游验签）" |
| 实现冲突 | 多候选且无 `@Primary` | "SsoUserHandler 实现冲突（存在多个且无 @Primary）" |
| 装配故障 | SPI bean 自身创建失败/`resolveUserName()` 抛运行时异常 | **不转业务拒绝**，按框架口径上浮为系统错误 |

`/web/auth/login`（bidr 形态）走 `IdentityBridgeLoginService`：有 `IdentityBridge` bean 时经桥验票 → `BridgeUserPrincipal` → `issueFor(username)`；无桥时回落 `SsoUserHandler` 链。两条链共用同一套三分支与租户闸。

**什么时候该用它**：身份已经在当前请求线程里（idm 验签、自接 IdP 的过滤器/拦截器）。**子系统浏览器跳转接 SSO 不要用它**——那条路径应该走 OAuth2 授权码。

### 3.7 已退役协议（不要再接）

以下协议/组件已删除，**没有回滚窗口**：

| 已退役 | 原用途 | 现状 |
|--------|--------|------|
| `POST /web/sso/ticket` | M15 出票面：登录态 → 短命 RS256 票（`username`+`exp`，ttl 60 档实际最长 90 秒） | 删除 |
| `POST /web/sso/introspect` | M15 令牌回查（三态 + 分层身份明细） | 删除（12 文件） |
| `SsoJwtUserHandler` / `SsoJwtUtil` / `SsoConfigKeys` | 子系统自读票 handler 及配套工具 | 删除 |
| `sso.jwt.public-key` 配置键 | 子系统静态公钥 | 四档 yml 整族禁入（守卫测试 `SsoConfigSurfaceConventionTest`） |
| 前端 `#/sso/authorize` 中转页 | 出票中转 | 框架 router 已删 |

**为什么退役**：OAuth2 授权码 + JWKS 一条链已经覆盖同样的需求，且解决了旧票通道的两个硬伤——旧票只有 60 秒寿命、没有受众判据、靠 origin 白名单放行；新链有标准的 client 认证、单次 code、可轮换公钥、全串 redirect 校验，并以 `app_id` 承担受众闸（验票侧强制比对，见 §3.4）。维护两条通道会让"回调白名单"出现两套口径（origin 等值 vs 全串等值），极易配错。

🔴 **旧文档里的这些规则只属于已退役的 ticket 通道，不要套到 OAuth2 上**：
- "回调地址登记 origin（`#` 之前）"——OAuth2 是**全串精确匹配**；
- "回环地址（127/localhost/::1）豁免登记"——OAuth2 现在也豁免，但范围更大：**localhost／127.0.0.0/8／10.0.0.0/8／::1** 且 scheme 为 http(s) 即免登记（端口/路径任意）；`localhost` 与 `127.0.0.1` 仍是两个不同串，只是都无需登记；
- "票最长存活 90 秒"——OAuth2 access_token 默认 3600 秒；
- "`sso_redirect_uri` 参数名"——OAuth2 用标准 `redirect_uri`。

如果手上还有按旧协议写的接入，按 [子系统SSO对接指南.md](子系统SSO对接指南.md) 迁移。

---

## 四、子系统 SSO 对接四步（总览）

完整操作见 [子系统SSO对接指南.md](子系统SSO对接指南.md)。这里只给一张总表，防止步骤遗漏：

| 步骤 | 在哪一侧 | 做什么 | 关键产物 |
|------|----------|--------|----------|
| 1. 配 partner 渠道 | 主系统（墨枢） | `identity.clients[]` 种子，或 partner 管理页/ SQL 建 `ac_partner` + `ac_sso_redirect` | `app_key`（=client_id）、`app_secret`（=client_secret）、回调白名单 |
| 2. 取公钥 | 主系统 → 子系统 | **不是** partner open-api；公钥走公开 `GET /web/oauth2/jwks.json`，子系统只配 `server-base-url` 运行时拉 | `my.idp.bearer.server-base-url` |
| 3. 注入私钥 | 主系统（墨枢） | `project-setup` skill 的 `--gen-idp-keypair` 生成 RSA 2048，写 `server/.env` + `sso-key/` | `IDENTITY_IDP_PRIVATE_KEY` / `IDENTITY_IDP_PUBLIC_KEY` |
| 4. 配子系统 | 子系统 | 后端三组配置 `my.oauth2.client.*`＋`my.idp.bearer.*`＋`my.authz.client.*`（零租户回判）；依赖两行 `authorization-client`＋`identity-mirror`；前端 `VITE_ssoLoginUrl`（authorize 直链）；回调换会话直接复用 `SubsystemSessionService` | 本系统登录态 |

> **两处与口头说法不一致，按实际实现为准**：
> 1. 公钥**不是**经 partner open-api 拉取。partner 的 open-api（`x-api-key`）是身份快照/项目镜像数据面；公钥是公开 JWKS，无需任何凭证。
> 2. 私钥生成脚本**不在** `server/core/tools/`（那里只有 `dep-direction-check.mjs`、`fqn-duplicate-check.mjs`），在 `project-setup` skill 的 `scripts/create-bidr-project.sh --gen-idp-keypair`。已有部署不想重跑 setup 的，用手工等价命令（文档给了 node 一行式）。

---

## 五、安全与运维纪律

1. **私钥只放 `server/.env` 与 `sso-key/`**（两者都被 `.gitignore` 覆盖）。仓库、yml、聊天记录、工单里不得出现私钥；公钥是公开信息，可以发。
2. **`app_secret` 是 client secret**：`PartnerSeeder` 对已存在的 `ac_partner` 行**会回写** `app_secret`（yml 是密钥正源）。轮换走环境变量（如 `INKHUB_EPC_CLIENT_SECRET`），**不要改成空默认值**——空值会把库里 secret 清空。
3. **`redirect_uri` 全串精确匹配**：scheme/host/port/path/结尾斜杠/大小写全部影响命中；`ac_sso_redirect.redirect_uri` 列是 `utf8mb4_bin`。不要用通配、前缀、origin 思维去登记。
   例外＝本地/内网回调免登记（`localhost`／`127.0.0.0/8`／`10.0.0.0/8`／`::1`，http(s)、端口任意）；套娃变体（`localhost.evil.com`、`10.evil.com`、十进制整数、八进制前导零）不豁免。
4. **`state` 由客户端动态生成**，不要预置在 `VITE_ssoLoginUrl` 里（会双 `state`，服务端取首个）；它用于防 CSRF 和回跳原页。
5. **NTP 同步**：JWT `exp` 判定有 30 秒时钟偏移容忍；跨机部署建议两侧都做 NTP 同步。token 默认 3600 秒，不要再按旧票的 90 秒口径做超时设计。
6. **密钥轮换 = 全员重登**：换私钥后所有已签 token 立即失效；接入方无需改配置，JWKS 缓存默认 1 小时，也可重启/显式刷新。
7. **`identity.idp.private-key` 未配置**：启动只 warn，authorize 仍能发 code，但 token 端点 400、JWKS 503。生产上线前必须配。
8. **`my.idp.bearer.enabled=true` 必须有钥来源**：`server-base-url` 或 `public-key-base64` 二选一，否则启动 fail-fast。
9. **子系统不得写主系统的 `ac_*` 表**：身份镜像/租户镜像写自己的库（epc 写 `idm_*`），主系统只通过 open-api 提供只读数据。
10. **不要在任何 yml 声明 `sso.jwt.public-key`**：该键随旧协议退役，四档 yml 整族禁入（守卫测试会拦）。
11. **验票必须带受众闸（`app_id`）**：墨枢同钥给所有接入方签票，只验签不验受众会让 A 子系统的票被 B 接受。期望值配 `my.idp.bearer.app-id`（或复用同宿主的 `my.oauth2.client.client-id`）；两者都缺时验签腿**拒绝启动**，不要为了"先跑起来"把闸关掉。
12. **平台管理员身份只从 provider 判**：零租户豁免必须回 `/authz/check`（`scope=""`＋`permission="admin"`）实时判，判据不看 uid 数值、不看本地角色表、不写死"uid==1 是管理员"。provider 未装配/不可达一律按非管理员处理（fail-closed）；也不会因为"空租户集"就推断管理员。
13. **refresh 必须钉票类型且一次性**：共享编排的 `refresh` 只收 `WEB_REFRESH_TOKEN`（accessToken 当 refresh 用一律拒），验过即销毁旧票再签新票；不要自建"任何有效票都能续期"的刷新口。

---

## 六、故障排查

| 现象 | 最可能原因 | 处理 |
|------|-----------|------|
| 访问 authorize 反复 302 到登录页 | 浏览器没有 `INKHUB_TOKEN` cookie；或登录态已失效 | 先在墨枢完成登录；确认登录响应种下 `INKHUB_TOKEN`；header 优先，cookie 只是整页导航的回退 |
| `400 client_id 未登记` | `ac_partner` 无该 `app_key`（未 seed、拼错、被停用/清库） | 检查 `identity.clients` 种子或 partner 管理页；重启触发 `PartnerSeeder` |
| `400 redirect_uri 未登记` | 与白名单行不是全串相等：结尾斜杠、端口、scheme 大小写、URL 编码、路径不同（本地/内网 host 有免登记豁免，不走此闸） | 用浏览器实际跳转的 `redirect_uri` 原串逐字符对比 `ac_sso_redirect.redirect_uri` |
| token `invalid_client` | `client_secret` 不匹配；`PartnerSeeder` 回写了新值；环境变量为空 | 对齐 `ac_partner.app_secret` 与子系统配置；检查 `INKHUB_*_CLIENT_SECRET` |
| token `invalid_grant`（code 无效） | code 已用过/超过 300 秒；并发回调；服务重启清 Redis | 重新走 authorize；确认回调只消费一次 |
| token `invalid_grant`（redirect_uri 不一致） | authorize 与 token 传的 `redirect_uri` 不同串 | 两处使用同一常量/同一 env 值 |
| token `server_error` | `identity.idp.private-key` 未配置 | 按 [子系统SSO对接指南.md](子系统SSO对接指南.md) §三 注入私钥并重启 |
| JWKS 503 | 同上（私钥未配置） | 同上 |
| 子系统启动失败：`my.idp.bearer.enabled=true 但未配置钥来源` | 只开了开关，没配 `server-base-url` / `public-key-base64` | 二选一补配置 |
| 子系统验票恒失败 | `server-base-url` 少了 context path（如漏 `/inkhub`）；网络不通；公钥轮换未跟上 | `curl <base>/web/oauth2/jwks.json` 应返回 200；检查缓存 TTL/重启 |
| 子系统拒绝"明明有效"的票 | `app_id` 受众不符：票是墨枢签给**另一个** client 的；或本系统 `app-id`/`client-id` 配错 | 核对票的 `app_id` 与本系统 `ac_partner.app_key`；检查 `my.idp.bearer.app-id`、`my.oauth2.client.client-id` |
| 启动失败：无法确定"期望接入方"(app_id) | 验签腿开启但 `app-id` 与 `my.oauth2.client.client-id` 都空 | 二选一补齐（§3.5），不要关闭验签腿绕开 |
| `tenant_ids` 为空 | 用户没有 `ac_user_tenant` 行；或 open 模式（本来就不绑） | tenant 模式下补归属行（Portal 成员管理/`tenant-backfill.js`）；open 模式忽略 |
| tenant 模式 SSO 首登被拒 | "账号需由管理员/主系统创建并分配租户后使用" | 先建号/补 `ac_account` 主数据，或由管理员分配租户；不要在 tenant 模式期待 JIT 建号 |
| 存量账号登录被拒：未分配租户 | `assertSsoTenantReady` / 补绑失败 | 补 `ac_user_tenant`；确认 `identity.tenant.code` 指向有效租户；`line-enabled=true` 时登录会自动补绑 |
| 生产新号没有租户 | prod 档未声明 `identity.tenant.*` | 补 `IDENTITY_TENANT_CODE/NAME` 或 yml（§2.10） |
| 子系统里零租户用户（含平台管理员）全被拒 | `my.authz.client.enabled` 未开（无 `AuthzCheckApi`）；或 `server-base-url`/`app-key`/`app-secret` 缺导致 `/authz/check` 失败 | 补 `my.authz.client.*` 并重启；日志搜"管理员判定"看 provider 在位/调用结果 |
| 子系统注入不到 `SubsystemSessionService` | 只引了 `authorization-client`（镜像是 optional 不传递）；或宿主没把三个镜像 repo 扫成 bean | pom 补 `identity-mirror`；核对 `idmUserMirrorService`/`idmUserTenantMirrorService`/`idmTenantMirrorService` bean 名 |
| 主系统（墨枢）日志出现 `SubsystemSessionService` | 不应发生（无镜像，自动装配退位） | 查 classpath 是否混入 `identity-mirror`（主 fat jar 应 0 条） |

---

## 七、证据与现状（诚实标注）

**当前成立（源码事实，2026-11-04 快照）**：

- OAuth2 三端点（authorize/token/jwks）+ 授权码单次有效 + RS256 + JWKS 公钥发布；
- `authorization-client` 的 `OAuth2Client` / `IdpTokenVerifier` / `IdpBearerVerifier` / `JwksKeyProvider`（JWKS 动态取钥、1 小时缓存、单飞、上次快照兜底、静态兜底、启动不依赖主系统在线）；
- **受众闸**：`IdpTokenVerifier` / `IdpBearerVerifier` 强制票面 `app_id` 等于本宿主 client_id（缺失或不符一律拒）；期望值缺省回退 `my.oauth2.client.client-id`；**真机互投已验（2026-11-04）**：墨枢 6.60 签票 × epc_ai 本地 8081 验票 6/6（epc-ai 票换会话/直票均过、aud-test 票 JWKS 验签为真但被 epc 受众闸拒）；OAuth2 受众闸 E2E **11/11**（含管理员零租户刷新经 `/authz/check`、跨子系统票拒收）——脚本与 6/6 互投日志留档于 `.qoder/dev-logs/20261104-audience-gate-e2e/`，11 腿那轮输出未持久化（本地，未入库）；
- **共享会话编排已下沉 core（2026-11-04）**：`SubsystemSessionService`（镜像闸 → 租户闸 → 零租户 `/authz/check` 回判 → 双票签发）＋`SubsystemSessionAutoConfiguration` 条件装配；主系统无镜像 ⇒ 自动退位（主 fat jar 0 条 `identity-mirror`、日志 0 次 `SubsystemSessionService`，产物级已验）；
- `authorization-client` 模块全量 **86 腿全绿**（2026-11-04 实跑）：原有 63 腿（含 IdP 验票三件 22 腿、受众 6 腿、JWKS 信封 1 腿）＋本链新增 23 腿（`SubsystemSessionService` 19＋条件装配 4）；
- `my.idp.bearer.enabled=true` 无钥来源 ⇒ 启动 fail-fast；
- 租户种子 `TenantSeeder`、partner 种子 `PartnerSeeder`、登录补绑 `TenantBindOnLoginService`、三档注册模式与 SSO 租户闸；
- 租户切换 `/web/tenant/my/list`、`/web/tenant/my/switch`；租户管理面 `/web/tenant/portal/*`；
- epc_ai 已按"authorize 直链 + 后端 exchange + JWKS 验签 + 租户镜像 + core 共享编排"落地：epc 侧只剩 `BidrLoginService` 适配层（取 `IdmHolder` 用户名 → `SubsystemSessionService.login`），参考实现路径见子系统指南 §七。

**已退役（不要再引用为可用能力）**：

- `/web/sso/ticket`、`/web/sso/introspect`、`SsoJwtUserHandler`、`SsoJwtUtil`、`SsoConfigKeys`、`sso.jwt.public-key`、前端 `#/sso/authorize`。

**未跑/待补**：

- 生产档 `identity.tenant.*` 尚未声明（§2.10），上线前必须补；
- **受众闸真机腿已跑（2026-11-04）**：墨枢 6.60 × epc_ai 两真实子系统互投 6/6 通过（正向 epc-ai 换会话/直票、负向 aud-test 真签名票被拒且日志命中 `app_id 缺失或与期望接入方不符`）；真机经 `IdpTokenVerifier`（epc 的换会话与嵌入入口都用它），`IdpBearerVerifier` 暂无消费方，仍只有离线腿；后续接第三子系统时按同一脚本形态复跑即可（脚本见 `.qoder/dev-logs/20261104-audience-gate-e2e/audience-e2e.mjs`）；
- 真实浏览器 UI 的部分验收腿（如 partner 管理页同屏登记区）仍交手测；
- 密钥按部署生成的轮换流程目前是手工流程，没有自动轮换/多钥并存的运维面（JWKS 已按 `kid` 选钥，为将来多钥留了结构）。

> 本文只写"源码能证明的"和"已明确退役的"。测试/真机证据请结合各里程碑的 `.qoder/dev-logs/` 与 `docs/开发里程碑跟踪.md` 查看；不要把旧 ticket 时代的"90 秒"口径当成当前 OAuth2 的行为。旧 ticket 的"回环豁免"在 OAuth2 里已重做并扩面：localhost／127.0.0.0/8／10.0.0.0/8／::1 且 http(s) 免登记（2026-11-04），其余 host 仍全串精确匹配。
