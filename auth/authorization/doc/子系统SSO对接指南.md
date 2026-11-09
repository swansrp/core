# 子系统 SSO 对接指南（墨枢 InkHub 作 IdP）

> **读者**：要把自己的系统（下称"子系统"）登录接到墨枢（InkHub，下称"主系统"）的开发者/运维。
> **前置**：主系统已部署、能正常登录；你拿到主系统根地址（含 context path，如 `http://10.3.6.60/inkhub`）。
> **关联**：[Auth对接指南.md](Auth对接指南.md)（能力地图、多租户、协议细节）、[SSO接入指南.md](SSO接入指南.md)（SPI 登录口深挖）。
> **快照**：2026-11-04。本指南只写源码能证明的当前行为；旧 ticket/introspect 协议已退役，本文不涉及。

---

## 〇、先纠三条，再看职责表

对接方最常见的三个误解，按实际实现纠正：

| 常见说法 | 实际实现 | 证据 |
|----------|----------|------|
| 公钥通过 partner 的 open-api 拉取 | **不是**。partner open-api（`x-api-key`）是身份快照/项目镜像数据面，不提供公钥；公钥走公开端点 `GET /web/oauth2/jwks.json`，零鉴权 | `OAuth2IdpController#jwks`；`IdentitySnapshotController` / `OpenApiProjectController` |
| 私钥由 `server/core/tools/` 里的脚本注入 | **不是**。`server/core/tools/` 只有 `dep-direction-check.mjs`、`fqn-duplicate-check.mjs`；密钥对生成入口是 `project-setup` skill 的 `--gen-idp-keypair`（§三） | 目录实测 |
| 子系统需要拿到私钥才能验票 | **不需要**。私钥只在主系统；子系统用公钥验签。私钥外发＝伪造任意用户 | §3.1 |

**谁配什么（一页速查）**：

| 事项 | 主系统（墨枢） | 子系统 |
|------|----------------|--------|
| 登记 client（`app_key`/`app_secret`） | ✅ `identity.clients` 种子 / 管理 API / SQL | ❌ 只接收这两个值 |
| 登记回调白名单（`redirect_uri`） | ✅ 同上（白名单在主系统库里；**本地/内网回调免登记**，见 §1.5） | ❌ 只保证请求里的串与登记串全串一致 |
| 生成/持有签名私钥 | ✅ `server/.env` 的 `IDENTITY_IDP_PRIVATE_KEY` | ❌ **绝不接触** |
| 提供公钥 | ✅ 公开 JWKS 端点 | ✅ 配 `server-base-url` 运行时拉（推荐） |
| 发起登录、拿 code | ❌ | ✅ 前端整页跳 authorize |
| code 换 token | ✅ 签发 | ✅ 后端调 token 端点 |
| 本地验签、建自己的会话 | ❌ | ✅ `IdpTokenVerifier` + `SubsystemSessionService`（共享编排；或本地登录链） |
| 租户归属（`ac_user_tenant`） | ✅ 主系统数据 | ✅ 按 `tenant_ids`/租户清单做自己的镜像；零租户闸用共享编排＝内置（§4.6） |

**全链路时序（授权码 + JWKS）**：

```
浏览器           子系统前端            子系统后端                主系统（墨枢）
  │ 点"主系统登录"    │                     │                         │
  │────────────────>│  整页跳 authorize    │                         │
  │                 │─────────────────────┼────────────────────────>│
  │                 │                     │   未登录 → 302 {context-path}/#/login?redirect_uri=…
  │                 │                     │   已登录 → 校验 client + redirect_uri
  │<────────────────┼─────────────────────┼─────────────────────────│
  │  302 回 redirect_uri?code=…&state=…   │                         │
  │────────────────>│  POST /exchange     │                         │
  │                 │  {code,state}       │                         │
  │                 │────────────────────>│  POST /web/oauth2/token │
  │                 │                     │  form: code+client_id+… │
  │                 │                     │────────────────────────>│
  │                 │                     │  {access_token(JWT),…}  │
  │                 │                     │<────────────────────────│
  │                 │                     │  本地验签（JWKS 取公钥）│
  │                 │                     │  （可选）POST /open-api/v1/tenants
  │                 │                     │  建本地会话              │
  │                 │  本地 token         │                         │
  │<────────────────┼─────────────────────│                         │
```

---

## 一、第一步：登记 partner 渠道（主系统侧）

### 1.1 登记的是什么

OAuth2 的 `client_id` / `client_secret` / `redirect_uri` 三件套，在主系统侧落在两张既有表（**零新表**）：

| OAuth2 概念 | 主系统落点 | 字段 | 说明 |
|-------------|------------|------|------|
| `client_id` | `ac_partner` | `app_key` | 应用的唯一标识；主系统按 `selectOne` 取单行，**不得重复**（表上只有普通索引，重复会抛异常） |
| `client_secret` | `ac_partner` | `app_secret` | 恒时比较；`identity.clients` 的 yml 值是密钥正源，种子会回写 |
| `redirect_uri` | `ac_sso_redirect` | `redirect_uri` + `valid` | 一个 partner 可登记多行（多环境/多端口）；判定＝**全串精确等值**且 `valid='1'` |

- 表结构：`AcPartnerSchema` / `AcSsoRedirectSchema`；`ac_sso_redirect.redirect_uri` 列级 `COLLATE utf8mb4_bin`（大小写敏感是判据承重件）。
- 主系统在 authorize 与 token 两个端点都会校验这三件套（token 端点会**再查一次**白名单，见 `OAuth2TokenService#grantByAuthorizationCode` 第 4 步）。

### 1.2 方式 A（推荐）：`identity.clients` yml 种子

在**主系统**的部署配置里声明（本仓库落在 `server/inkhub-common/src/main/resources/config/application-{dev,pre,prod}.yml`；其他宿主在各自的配置档）：

```yaml
identity:
  clients:
    - app-key: my-subsystem          # ＝OAuth2 client_id（部署方自定，建议与子系统编码一致）
      app-secret: ${MY_SUBSYSTEM_CLIENT_SECRET:}   # ＝OAuth2 client_secret；走环境变量，禁止空默认值
      redirect-uris:
        - http://127.0.0.1:18081/oauth2/callback  # 本地联调
        - http://10.3.6.60:18081/oauth2/callback  # 预发/生产
```

`PartnerSeeder`（启动期 `ApplicationRunner`）的幂等语义：

| 场景 | 行为 |
|------|------|
| `identity.clients` 为空/未配置 | 整体跳过，`log.info`（非对接形态，零影响） |
| `app_key` 不存在 | 新建行：`platform=''`、`status=1`、备注＝`partner-seed 播种` |
| `app_key` 已存在 | **只同步 `app_secret`**（配置是密钥正源）；`platform`/`status`/`remark` 保留现库人工值，不回写 |
| `redirect_uris` 某条已登记（`partner_id + uri + valid='1'`） | 跳过，不重复插 |
| `redirect_uris` 某条未登记 | 追加一行（`valid='1'`） |

🔴 **四个必须知道的边界**：

1. yml 是 jar 内资源，改完必须重编 + 重启（`run-local --skip-build` 吃不到新声明）。
2. **种子只增不删**：从 yml 删掉某条 `redirect-uri` 不会撤销库里的白名单行；撤销要手工 `UPDATE ac_sso_redirect SET valid='0' WHERE ...`（或删行）。
3. **种子不回写 `status`**：库里被人工停用的 partner，重启种子也不会自动恢复；反之新建行默认 `status=1`。
4. **`status` 不是 OAuth2 的运行期闸门**：authorize / token / open-api 三处都只校验 `app_key`/`app_secret`/`redirect_uri`，**不校验 `ac_partner.status`**。要真正切断某个 client，请撤销其全部白名单行（`valid='0'`）或更换 `app_secret`；只点"停用"不会拦截登录。

### 1.3 方式 B：partner 管理 API（有管理端时）

```http
POST /web/partner/admin/add
Authorization: <平台管理员 token>
Content-Type: application/json

{"platform": "", "remark": "my-subsystem 对接"}
```

- 类级 `@Auth(AuthAdmin)`＝平台管理员才能调。
- 返回**服务端随机生成**的 `{appKey(8位), appSecret(16位)}`——**不能指定 app_key**。对"子系统编码＝client_id"的稳定命名诉求，请用方式 A 或 C。
- 另有 `/query`、`/enable`、`/disable`、`/update`、`/delete`（继承 `BaseAdminController`）。
- **没有回调白名单管理 API**：`ac_sso_redirect` 目前只能靠种子或 SQL 登记。管理页如已提供"登记区"，其底层同样是这两张表。

### 1.4 方式 C：直接 SQL（不推荐，须人工复核）

```sql
-- 1) partner（app_key 必须唯一：主系统按 selectOne 取单行）
INSERT INTO ac_partner (app_key, app_secret, platform, remark, `status`, create_by, create_at)
VALUES ('my-subsystem', '<secret>', '', 'my-subsystem 对接', 1, 'admin', NOW(3));

-- 2) 回调白名单（partner_id 取上一步自增 id；一行一个全串）
INSERT INTO ac_sso_redirect (partner_id, redirect_uri, remark, valid, create_by, create_at)
VALUES (<partner_id>, 'http://127.0.0.1:18081/oauth2/callback', '本地联调', '1', 'admin', NOW(3));
```

🔴 数据类改动按项目纪律**人工复核**后执行；不要用 `REPLACE INTO`（会换 id，白名单关联断掉）。

### 1.5 `redirect_uri` 判定规则（踩坑率最高）

| 规则 | 说明 |
|------|------|
| 全串精确等值 | 入参与登记串 `trim()` 后逐字符相等；**无前缀匹配、无通配、无 origin 匹配** |
| **本地/内网免登记**（2026-11-04） | host＝`localhost`（不区分大小写）／`127.0.0.0/8`／`10.0.0.0/8`／`::1`，scheme 仅 http(s)、端口/路径任意 ⇒ 直接放行，无需 `ac_sso_redirect` 行 |
| 大小写敏感 | 列级 `utf8mb4_bin`；`http://HOST` ≠ `http://host` |
| 端口/路径/尾斜杠都算不同 | `http://h:18081`、`http://h:18081/`、`http://h:18081/cb` 是三个不同的串（本地/内网 host 例外：免登记） |
| `localhost` ≠ `127.0.0.1` | 按原串比较，不解析 DNS；两者都免登记，但仍是两个不同串 |
| authorize 与 token 两处必须同值 | 建议两处读取**同一个配置项**（`my.oauth2.client.redirect-uri`），不要一处写死 |
| 可登记多个环境 | 同一 partner 下本地/预发/生产各一行，互不影响 |
| 撤销＝软删 | `valid` 置 `'0'`；再次启用请插新行（登记面查重只看 `valid='1'`） |

> 旧 SSO 出票面（已退役）用 origin 等值；**OAuth2 通道沿用全串等值，但额外有本地/内网免登记豁免**（范围见上表）。套娃变体（`localhost.evil.com`、`127.0.0.1.evil.com`、`10.evil.com`、十进制整数、八进制前导零）不豁免，照走白名单。

### 1.6 登记完成自检

1. `SELECT id, app_key, app_secret, status FROM ac_partner WHERE app_key='my-subsystem';` —— 有且仅一行。
2. `SELECT redirect_uri, valid FROM ac_sso_redirect WHERE partner_id=<id>;` —— 目标串逐字符一致、`valid='1'`。
3. 浏览器**先登录主系统**，再访问：

   ```
   {base}/web/oauth2/authorize?response_type=code&client_id=my-subsystem&redirect_uri=<URL编码后的回调串>&state=test
   ```

   - 302 到你的回调地址并带 `code=…&state=test` ⇒ 三件套全对；
   - `400 client_id 未登记` ⇒ `app_key` 不存在/拼错；
   - `400 redirect_uri 未登记` ⇒ 与白名单不是全串相等（常见：尾斜杠、端口、URL 编码差异）；本地/内网 host（localhost／127／10）不走此闸。

---

## 二、第二步：取公钥（JWKS，不是 partner open-api）

### 2.1 端点与鉴权

```
GET {base}/web/oauth2/jwks.json
```

- `@Auth(AuthNone)` **公开端点，无需任何凭证**；JWKS 按部署生成，每个部署一套。
- `base` 必须是**含 context path** 的根地址（如 `http://10.3.6.60/inkhub`），拼出来是 `/inkhub/web/oauth2/jwks.json`。
- 主系统未配置私钥时返回 **503**（`identity.idp 签发钥匙未配置，JWKS 端点不可用`）。

响应形状（RSA 2048，单钥）：

```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "u1SU…（base64url 无填充）",
      "e": "AQAB",
      "kid": "cb0877fc2b6cb9ac",
      "use": "sig",
      "alg": "RS256"
    }
  ]
}
```

- `kid` ＝ SHA-256(`X.509 SPKI DER`) 的前 8 字节 hex（16 字符），与 access_token header 的 `kid` 同一算法（`IdpJwtUtil.computeKid`）。
- token 的 `iss` 固定为 `inkhub`；`alg` 固定 `RS256`。

### 2.2 子系统怎么用：两种形态

| 形态 | 配置 | 适用 | 换钥行为 |
|------|------|------|----------|
| **动态（推荐）** | `my.idp.bearer.server-base-url: {base}` | 默认；接入方零密钥 | 运行时拉 JWKS，缓存 1 小时 |
| 静态兜底 | `my.idp.bearer.public-key-base64: <X.509 SPKI base64>` | 纯离线、排查、主系统长期不可达 | 手工换；不随主系统换钥 |

`JwksKeyProvider`（client 库内）的取钥链：

1. 内存缓存命中且未过期（默认 TTL 1 小时）——零网络；
2. 未配 `server-base-url`（纯静态）⇒ 直接用静态公钥；
3. 缓存过期 ⇒ 刷新一次 JWKS（单飞，并发只发一个请求）；
4. 刷新失败 ⇒ 沿用上一次成功快照 + WARN；
5. 仍无 ⇒ 静态公钥兜底；都没有 ⇒ 拒票（fail-closed）。

🔴 **启动不依赖主系统在线**：构造只建空缓存，首次验签才拉取；主系统抖动不会导致子系统起不来。JWKS 拉取失败只影响验票，不影响启动。

### 2.3 开关与 fail-fast

```yaml
my:
  idp:
    bearer:
      enabled: true
      server-base-url: 'http://10.3.6.60/inkhub'
```

- `enabled` 默认 `false`＝不装配，宿主不配置零影响。
- `enabled=true` 但 `server-base-url` 与 `public-key-base64` **都为空** ⇒ **启动直接失败**，错误信息点名该配什么（`IdpBearerAutoConfiguration#assertKeySourceConfigured`）。
- `enabled=true` 但期望接入方（`my.idp.bearer.app-id`，缺省回退 `my.oauth2.client.client-id`）**也解析不出** ⇒ **启动直接失败**（`#assertAudienceConfigured`）——没有受众闸时"签给其它子系统的票"会被本系统收下。
- 同一个开关同时装配 `IdpTokenVerifier`（换票后验 JWT）与 `IdpBearerVerifier`（业务接口验 `Authorization: Bearer`）。两条腿共用一个取钥器、同一受众闸。

### 2.4 partner open-api 是什么（为什么不是公钥来源）

| 端点 | 鉴权 | 用途 | 在哪个模块 |
|------|------|------|------------|
| `GET /open-api/v1/identity/snapshot` | `x-api-key: Base64(appKey:appSecret)` | 用户/租户/归属关系全量快照（身份镜像） | core `authorization` |
| `GET /open-api/v1/sync/events` | `x-api-key` | 变更事件拉取 | core `authorization` |
| `POST /open-api/v1/projects` | `x-api-key` | 按 `{tenantCode}` 拉项目/班组镜像 | InkHub 业务模块 |
| `POST /open-api/v1/tenants` | `Authorization: Bearer <IdP access_token>` | 当前账号的**实时**租户清单 | InkHub 业务模块 |

它们是**数据面**（业务数据同步），不是登录/密钥面。公钥请只从 JWKS 取；私钥永远不出主系统。

> `/open-api/v1/tenants` 读的是 `ac_user_tenant` **实时绑定**，不是 JWT 里的 `tenant_ids` 快照。需要"登录当下的最新租户清单"时用它。

---

## 三、第三步：注入私钥（只有主系统做）

### 3.1 谁需要私钥

- **主系统（IdP 角色）**：一对一 RSA 密钥对，私钥用于签发 access_token（RS256），公钥公开。
- **子系统**：只用公钥验签，**不需要也不应该拿到私钥**。任何"把私钥发给接入方"的做法都等于允许对方伪造任意用户。

私钥法定位置：主系统 `server/.env` 的 `IDENTITY_IDP_PRIVATE_KEY`（PKCS#8 DER 的 base64，容忍 PEM 头尾/空白）；备份放 `sso-key/`（均被 `.gitignore` 覆盖）。生产如果走容器，由部署脚本把 `.env` 注入环境变量，yml 只写 `${IDENTITY_IDP_PRIVATE_KEY:}`。

### 3.2 现有入口：`project-setup` skill

```bash
# 主系统（新建或已有项目均可）：
bash ~/.qoder/skills/project-setup/scripts/create-bidr-project.sh \
  --dir <项目根> --code <code> --gen-idp-keypair
```

该函数的**意图**：生成 RSA 2048；私钥写入 `server/.env` 的 `IDENTITY_IDP_PRIVATE_KEY`、公钥写 `IDENTITY_IDP_PUBLIC_KEY`；同时在 `sso-key/` 落 `priv-pkcs8.pem` 与 `pub-oneline.txt`；已有 `IDENTITY_IDP_PRIVATE_KEY` 时跳过（不覆盖）。

🔴 **当前脚本在 Node 24 上不可用（2026-11-04 实测）**：`gen_idp_keypair()` 里有三处缺陷——

1. `kp.publicKey.export({type:"der",format:"der"})` 的 `type` 非法（应为 `"spki"`），Node 直接抛 `ERR_INVALID_ARG_VALUE`；
2. `.replace(/s+/g,"")` 少写反斜杠（应为 `/\s+/g`），PEM 正文的换行不会被去掉；
3. `sed -n 1p` 取私钥时，PEM 正文以换行开头，第一行取到的是空串。

脚本没有检查 node 退出码 ⇒ 会**静默**写入空的 `IDENTITY_IDP_PRIVATE_KEY=` 和空的 `sso-key/` 文件，表现为"setup 成功但 IdP 端点 503"。修复前请用手工命令（§3.3）。

### 3.3 手工生成（已验证，二选一）

**方式一：Node（输出格式与上线脚本一致）**——在**主系统仓库根**执行：

```bash
node -e '
const fs = require("fs"), c = require("crypto");
const kp = c.generateKeyPairSync("rsa", { modulusLength: 2048 });
const der = kp.publicKey.export({ type: "spki", format: "der" });
const priv = kp.privateKey.export({ type: "pkcs8", format: "pem" })
  .toString().replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
const pub = der.toString("base64");
const kid = c.createHash("sha256").update(der).digest("hex").slice(0, 16);
fs.mkdirSync("sso-key", { recursive: true });
fs.writeFileSync("sso-key/priv-pkcs8.pem",
  "-----BEGIN PRIVATE KEY-----\n" + priv.replace(/(.{64})/g, "$1\n").trim() + "\n-----END PRIVATE KEY-----\n");
fs.writeFileSync("sso-key/pub-oneline.txt", pub);
fs.appendFileSync("server/.env",
  "\nIDENTITY_IDP_PRIVATE_KEY=" + priv + "\nIDENTITY_IDP_PUBLIC_KEY=" + pub + "\n");
console.log("kid=" + kid + " priv_len=" + priv.length + " pub_len=" + pub.length);
'
```

预期输出形如 `kid=cb0877fc2b6cb9ac priv_len=1624 pub_len=392`（长度随密钥而异，priv 应 >1600、pub ≈392）。

**方式二：OpenSSL（成熟方案，等长产物）**：

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out sso-key/priv.pem
openssl pkcs8 -topk8 -nocrypt -in sso-key/priv.pem -outform DER | base64 -w0 > sso-key/priv.b64
openssl pkey -in sso-key/priv.pem -pubout -outform DER | base64 -w0 > sso-key/pub.b64
```

然后把 `priv.b64` / `pub.b64` 的内容分别填入 `server/.env` 的两行（一行一个值，不要换行）。

> 公钥可留空：`identity.idp.public-key` 未配置时，主系统从私钥现场派生（PKCS#8 CRT 形态可派生）。显式给出时以配置为准。

### 3.4 落库与自检

1. `server/.env` 里两行非空（`grep -c '^IDENTITY_IDP_PRIVATE_KEY=.\+' server/.env` 应为 1）。
2. 重启主系统（`cd server && node tools/run-local.js`；部署环境重启容器）。
3. 自检：
   ```bash
   curl -s {base}/web/oauth2/jwks.json        # 200，keys[0].kid 有值
   curl -s -o /dev/null -w '%{http_code}\n' {base}/web/oauth2/jwks.json   # 503 ⇒ 私钥没生效
   ```
4. 启动日志不应再出现 `identity.idp.private-key 未配置`。

### 3.5 换钥流程与窗口

1. 生成新密钥对，覆盖 `server/.env` 两行（旧值先备份到 `sso-key/` 之外的安全位置）；
2. 重启主系统 ⇒ JWKS 立即返回新 `kid`，新签 token 用新钥；
3. **旧 token 立即失效**（用旧私钥签的票，新公钥验不过）；
4. 子系统动态取钥形态：JWKS 缓存 TTL 1 小时。缓存未过期时，遇到未知 `kid` **不会立即重拉**，会按 fail-closed 拒票；TTL 到期后的首次验签才刷新。因此换钥建议放维护窗口，或换钥后重启各子系统立即生效。纯静态形态必须手工同步新公钥，否则换钥后全部登录失败。
5. 当前没有自动轮换/多钥并存的运维面；JWKS 已按 `kid` 选钥，为将来多钥平滑轮换留了结构。

### 3.6 安全纪律

- 私钥只进 `server/.env` 与 `sso-key/`；仓库、yml 明文、聊天记录、工单、邮件里一律不得出现。
- 公钥是公开信息，可以发、可以贴进对接文档。
- 轮换＝全员重登；不要在生产高峰做。
- 备份私钥时单独加密存放；泄露即视为所有账号可被伪造，按事故处理。

---

## 四、第四步：子系统要配什么（完成 SSO 登录）

### 4.1 依赖（bidr 系子系统：两行）

```xml
<!-- 1) IdP 客户端件：授权码换票 + JWKS 验签 + /authz 判定 -->
<dependency>
    <groupId>com.bidr</groupId>
    <artifactId>authorization-client</artifactId>
    <version>${revision}</version>   <!-- 跟随 core 的 revision；framework 老项目需先接 core -->
</dependency>
<!-- 2) 身份镜像：共享会话编排（SubsystemSessionService）的账号源
     🔴 client 对它是 optional（不传递），子系统必须显式声明这一行 -->
<dependency>
    <groupId>com.bidr</groupId>
    <artifactId>identity-mirror</artifactId>
    <version>${revision}</version>
</dependency>
```

- `authorization-client` 自带 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，classpath 上有即按各自开关自动装配：`OAuth2ClientAutoConfiguration`（`my.oauth2.client.enabled`）/ `IdpBearerAutoConfiguration`（`my.idp.bearer.enabled`）/ `AuthzClientAutoConfiguration`（`my.authz.client.enabled`）/ `SubsystemSessionAutoConfiguration`。**这些自动装配类自身不需要宿主 `@EnableConfigurationProperties` 或显式扫包**（但组件扫描面仍须覆盖 `com.bidr`，见下）。
- `SubsystemSessionAutoConfiguration` 是**条件装配**：classpath 有 `identity-mirror`、且宿主已有 `TokenService` 与三个镜像 repo bean（`idmUserMirrorService` / `idmUserTenantMirrorService` / `idmTenantMirrorService`，默认 bean 名）才注册 `SubsystemSessionService`；缺任一即退位。主系统（墨枢）经 `authorization → authorization-client` 拿到本 jar，但 classpath 没有镜像 ⇒ 自动退位，**不建 `idm_*` 表、不注册该 bean**（真机日志与产物级证据见 §八）。
- 🔴 宿主组件扫描面须覆盖 `com.bidr`：共享编排要的 `TokenService` 实现（`TokenServiceImpl`）就在 client jar 里，靠组件扫描注册（bidr 系 `BaseApplication` 默认 `kernel.scan.base-packages=com.bidr`）。把扫描面收窄到宿主自己的包 ⇒ `TokenService` 缺席 ⇒ 条件装配静默退位。
- 🔴 因此子系统是**两行依赖**：`authorization-client` + `identity-mirror`。暂无聚合 starter；即便以后加空壳聚合包，底层仍是这两个 jar（只是把两行收成一行，语义不变）。
- 宿主必须已有 Spring Web MVC 栈（`spring-web` / `spring-webmvc`，client pom 已显式声明）。
- **非 bidr 技术栈**的子系统不必引这些 jar：按 §1/§2 的 HTTP 契约自己实现"跳 authorize / code 换 token / 拉 JWKS 验 RS256"，零租户/管理员豁免也自己判（§4.6）。

### 4.2 配置总表

```yaml
my:
  oauth2:
    client:
      enabled: true                                  # 换票腿；默认 false＝不装配
      base-url: 'http://10.3.6.60/inkhub'            # 含 context path；尾斜杠自动裁
      client-id: 'my-subsystem'                      # ＝ac_partner.app_key
      client-secret: '${MY_SUBSYSTEM_CLIENT_SECRET:}' # ＝ac_partner.app_secret；走环境变量
      redirect-uri: 'http://127.0.0.1:18081/oauth2/callback'  # 与白名单全串一致
  idp:
    bearer:
      enabled: true                                  # 验签腿；默认 false
      server-base-url: 'http://10.3.6.60/inkhub'     # 公钥走 JWKS；与上面的 base-url 通常同值
      app-id: 'my-subsystem'                         # 受众闸＝票面 app_id 必须等于它；缺省回退 my.oauth2.client.client-id
      # public-key-base64: '<X.509 SPKI base64>'     # 可选：纯离线/排查时静态兜底
  authz:
    client:
      enabled: true                                  # 零租户回判腿；默认 false＝不装配
      server-base-url: 'http://10.3.6.60/inkhub'     # 墨枢 /authz/check 基址（含 context path）
      app-key: '${my.oauth2.client.client-id}'       # 复用同一个 partner 机器凭证
      app-secret: '${my.oauth2.client.client-secret}'
      fail-mode: CLOSED                              # 不可达＝拒绝（默认）
```

| 键 | 缺省 | 缺失后果 |
|----|------|----------|
| `my.oauth2.client.enabled` | `false` | 无 `OAuth2Client` bean，换票代码注入失败 |
| `my.oauth2.client.base-url` | `""` | 换票时请求 URL 缺前缀 ⇒ 运行期异常（**无启动期校验**） |
| `my.oauth2.client.client-id` / `client-secret` | `""` | token 端点 400 `invalid_client` |
| `my.oauth2.client.redirect-uri` | `""` | authorize 与 token 必被拒（`redirect_uri 未登记`）；**两处必须同值** |
| `my.idp.bearer.enabled` | `false` | 无验签 bean；`IdpTokenVerifier` 注入失败 |
| `my.idp.bearer.server-base-url` / `public-key-base64` | `""` | `enabled=true` 时**启动 fail-fast** |
| `my.idp.bearer.app-id` | 回退 `my.oauth2.client.client-id` | 两者都空且 `enabled=true` ⇒ **启动 fail-fast**（受众闸必须能确定"期望接入方"） |
| `my.authz.client.enabled` | `false` | 无 `AuthzCheckApi`：零租户用户（**含平台管理员**）一律拒（fail-closed），有租户用户不受影响 |
| `my.authz.client.server-base-url` / `app-key` / `app-secret` | `""` | `/authz/check` 调用失败 ⇒ 同上按非管理员处理 |
| `my.authz.client.fail-mode` | `CLOSED` | 本会话链**每次实时调** `/authz/check`，失败即非管理员；该键只作用于 `RemoteAuthzChecker` 缓存链，不能把本链改成放行 |

> 三个 `enabled` 是**独立开关**：完整登录链＝换票（oauth2）+ 验票（idp.bearer）+ 零租户回判（authz）。只开前两个时，有租户用户一切正常，但**零租户用户全部被拒**（平台管理员也进不来）——这是刻意的 fail-closed。

### 4.3 登录腿的两种落地形态

| 形态 | `redirect_uri` 指向 | 谁换票 | 参考 |
|------|---------------------|--------|------|
| **A. 后端回调端点** | 子系统后端 URL（如 `/oauth2/callback`） | 后端在回调里直接 `exchangeCode` + 建会话，再 302 回前端 | 通用形态，服务端会话型系统 |
| **B. 前端回跳页 + 后端交换** | 子系统前端 URL（站点根/登录页） | 前端拿到 `code` 后 POST 给后端 `/exchange`，后端换票并回本地 token | epc_ai 现用（`POST /web/epc-ai/oauth2/exchange`） |

两种都合法；区别只是 `code` 在浏览器里多走一跳。无论哪种，`redirect_uri` 都必须与白名单全串一致（本地/内网 host 免登记，见 §1.5），且 token 请求里传**同一个串**。

### 4.4 后端代码骨架（形态 B，最简）

```java
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class SubsystemOAuth2Controller {

    private final OAuth2Client oauth2Client;          // my.oauth2.client.enabled=true 时由 client 库装配
    private final IdpTokenVerifier verifier;          // my.idp.bearer.enabled=true 时由 client 库装配
    private final SubsystemSessionService sessionService; // 宿主带 identity-mirror 时由自动装配注册
    private final YourTenantSyncService mirrorSync;   // 你自己的镜像写入口（用户行/租户关系）

    /** 前端回跳后调用：code + state → 本地会话 */
    @PostMapping("/exchange")
    public LoginRes exchange(@RequestParam String code,
                             @RequestParam(required = false) String state,
                             HttpSession session) {
        // 1) state 防 CSRF：与发起登录时放进 session 的一次性随机串比对
        String expected = (String) session.getAttribute("idp_state");
        session.removeAttribute("idp_state");
        if (expected == null || !expected.equals(state)) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "state 校验失败");
        }
        // 2) code 换 token（redirect_uri 用配置值，与 authorize 同串）
        OAuth2TokenRes token;
        try {
            token = oauth2Client.exchangeCode(code, oauth2Client.configuredRedirectUri());
        } catch (OAuth2ExchangeException e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "授权码换 token 失败：" + e.getMessage());
        }
        // 3) 本地验签（fail-closed：null 一律拒）
        IdpPrincipal principal = verifier.verify(token.getAccessToken());
        if (principal == null) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "IdP token 验签失败或已过期");
        }
        // 4) 身份镜像对齐：共享编排以本地镜像为唯一账号源，必须先同步再签发
        //    （拉 /open-api/v1/tenants 补租户关系是你的实现；失败降级策略也由你定）
        mirrorSync.syncOnLogin(principal);

        // 5) 共享编排签发本地会话：镜像闸 → 租户闸 → 零租户回 /authz/check 判管理员 → 双票
        return sessionService.login(principal.getUsername());
    }
}
```

`SubsystemSessionService` 的三条口径（`login(username)` 与 `refresh(refreshToken)` 同源）：

1. **账号源＝本地镜像**：按 `user_name` 查 `idm_user_mirror`，缺失/停用即拒——所以第 4 步的镜像同步必须在前；租户集也**现查镜像**（`idm_user_tenant_mirror × idm_tenant_mirror` 两侧 `valid='1'`），**不信任** JWT 里的 `tenant_ids` 快照。
2. **零租户闸内置**：非管理员空租户集 ⇒ 业务拒绝（"当前用户无有效租户归属…"）；空集用户回 `{my.authz.client.server-base-url}/authz/check`（`scope=""`＋`permission="admin"`）实时判平台管理员，判是才放行，且生成的本地票**不写** `TENANT_IDS`/`TENANT_ID`。判据不看 uid 数值、不看本地角色表。
3. **双票签发＋收尾**：就地用 core `TokenService` 造 access/refresh，烤入 `USER_ID`/`OPERATOR`/`NICK_NAME`/`USER_NAME`（有租户再写 `TENANT_IDS`/`TENANT_ID`），并 `TokenHolder.set(access)` 供同请求内 `getTokenValue()` 使用。

如果你已有 IdmHolder 形态的登录链（epc 就是），适配层只需一个方法：取 `IdmHolder` 用户名 → `sessionService.login(username)`（见 §七）；刷新端点直接委托 `sessionService.refresh(token)`。

### 4.5 `state` 与 CSRF

- `state` 由**子系统**生成（一次性随机串），存 session/Redis，回跳后比对并立即删除。
- 主系统只做原值透传（RFC 6749 §4.1.1），**不存储、不校验** `state`——防 CSRF 是客户端的责任。
- 不要预先把 `state` 写死在前端的 authorize 直链里；直链每次动态拼接。
- `state` 里可以顺带放"回跳原页"信息，但不要把敏感数据放进去（会出现在浏览器地址栏/日志）。

### 4.6 租户：`tenant_ids` 与租户清单

| 事项 | 事实 |
|------|------|
| access_token 的 `tenant_ids` | 是 authorize 那一刻 `ac_user_tenant` 的**快照**（可能为空数组） |
| OAuth2 通道会拦"零租户"吗 | **主系统不拦**：authorize 照发 code、token 照签票；零租户只是 `tenant_ids=[]` |
| 子系统侧谁拦 | **用共享编排（`SubsystemSessionService`）＝内置**：非管理员空租户集直接拒绝（文案"当前用户无有效租户归属…"）。不用共享编排的系统必须自行 fail-closed，不能把空 `tenant_ids` 当"没限制" |
| 平台管理员零租户怎么办 | 共享编排在空集时回 `{my.authz.client.server-base-url}/authz/check`（`scope=""`＋`permission="admin"`）实时判，判是才放行；本地票**不写** `TENANT_IDS`/`TENANT_ID`。判据不看 uid 数值、不看本地角色表；provider 未装配/不可达＝按非管理员拒（fail-closed） |
| 共享编排的租户集从哪来 | **现查本地镜像**：`idm_user_tenant_mirror`（关系 `valid='1'`）× `idm_tenant_mirror`（租户 `valid='1'`）取交集、按关系行顺序去重——不是 JWT 里的 `tenant_ids` 快照 |
| 拿实时租户清单 | `POST {base}/open-api/v1/tenants`，头 `Authorization: Bearer <access_token>`；返回统一信封 `{status, payload:{tenants:[{id,code,name}]}}` |
| 按租户拉项目 | `POST {base}/open-api/v1/projects`，头 `x-api-key: Base64(client_id:client_secret)`，体 `{"tenantCode":"..."}` |
| 主系统 `register-mode` | `tenant` 档：SSO 用户必须有租户绑定（SPI 登录口会硬拒）；`open` 档：全局账号，本来就不绑租户，不要强行要求 |

推荐做法（epc_ai 参考）：登录成功后
1. 先按 `principal` 写本地用户镜像；
2. 用 access_token 调 `/open-api/v1/tenants` 拉实时租户清单；
3. 按清单写本地 `租户关系` 镜像；
4. 清单为空不用另设策略：用共享编排时**零租户闸已内置**（非管理员拒绝、管理员回 `/authz/check`）；epc 就是不自建闸、直接交给 `SubsystemSessionService`。

> 主系统里"切换租户"只改主系统自己的 token 上下文，**不会**重签子系统的 access_token；子系统如需跟随，应在下次登录或自行提供映射入口。

### 4.7 前端配置与跳转

```js
// .env.development / .env.pre / .env.production
VITE_ssoDomain = 'http://10.3.6.60'
// authorize 直链模板（redirect_uri 必须 encodeURIComponent；state 运行时再拼）
VITE_ssoLoginUrl = 'http://10.3.6.60/inkhub/web/oauth2/authorize?response_type=code&client_id=my-subsystem&redirect_uri=' +
  encodeURIComponent('http://127.0.0.1:18081/oauth2/callback')
```

要点：

1. **整页跳转**（`window.location.href = url + '&state=' + state`），不要用 XHR/fetch——authorize 依赖浏览器的主系统登录态（`INKHUB_TOKEN` cookie 是整页导航的回退腿）。
2. `response_type` 必须是 `code`；传别的值会 302 回 `redirect_uri` 带 `error=unsupported_response_type`。
3. 未登录时主系统 302 到 `{context-path}/#/login?redirect_uri=<去掉前导斜杠的 authorize 路径+query>`（登录页前缀跟随主系统 context-path，如 `http://10.3.6.60/inkhub/#/login`）；在主系统登录页登录成功后自动回到 authorize，无需子系统干预。
4. 回调页从 URL 取 `code`/`state`，调后端 `/exchange`，成功后清掉 URL 上的 code（`history.replaceState`），再进业务首页。
5. `client_secret` **只放后端**，前端配置里不得出现。

### 4.8 子系统本地会话怎么做（四选一）

| 做法 | 说明 | 适用 |
|------|------|------|
| **复用共享编排（推荐）** | 自动装配的 `SubsystemSessionService.login(username)` / `refresh(token)`：镜像闸、租户闸、零租户管理员回判、双票签发与 `TokenHolder` 收尾全在内 | 所有以 `identity-mirror` 为账号源的 bidr 子系统（epc 用法） |
| 复用自身既有登录链 | 把 `IdpPrincipal` 当成"已认证身份"，调本地登录服务发本地 token（需自己实现租户/管理员闸） | 已有别的账号体系、镜像不是唯一账号源 |
| 自建轻量会话 | 校验通过后写 HttpSession 或自签本地 JWT | 简单系统 |
| 每次验 Bearer | 业务接口直接用 `IdpBearerVerifier` 验主系统 token | 仅做透传、无本地账号体系 |

若用 `IdpBearerVerifier`：入参可以是 `Authorization` 头原值（容忍 `Bearer ` 前缀），返回 claims `Map`（含 `tenant_ids`），失败一律 `null`。

### 4.9 验收清单（全部通过才算接完）

- [ ] 主系统 `curl {base}/web/oauth2/jwks.json` 返回 200 且 `kid` 非空（不是 503）。
- [ ] 未登录时点登录，能到主系统登录页；登录后自动回到 authorize。
- [ ] authorize 302 回子系统回调，URL 带 `code` 与**原样的** `state`。
- [ ] 后端换票成功，`access_token` 为三段 JWT；`expires_in` 约 3600。
- [ ] 本地验签通过；篡改任意一段 token 后验签必须失败（负向腿）。
- [ ] 拿一张签给**另一个**子系统的票（或把票面 `app_id` 改成本系统后重签）来验，必须被拒（受众闸负向腿）。
- [ ] `state` 不匹配时登录被拒（负向腿）。
- [ ] 用户在主系统无租户时，子系统的策略符合预期（拒绝或降级，不能"意外放行到无租户上下文"）。
- [ ] 用共享编排时：**非管理员零租户 ⇒ 拒绝**（"当前用户无有效租户归属…"）；**平台管理员零租户 ⇒ 放行**，且本地票 `TENANT_IDS` 不存在（`/authz/check` 判定路径，两条负向/正向腿）。
- [ ] 用共享编排时：`refresh` 端点**只收 refreshToken**——accessToken 当 refresh 用必须被拒（票类型闸），且旧 refreshToken **用一次即失效**（负向腿）。
- [ ] 重启主系统后子系统仍能启动（JWKS 启动不依赖主系统在线）。
- [ ] 主系统停掉时，子系统启动不失败；登录会失败并给出可读错误。
- [ ] 日志里不打印 `access_token` 全文（最多摘要指纹）。

---

## 五、故障排查

| 现象 | 最可能原因 | 处理 |
|------|-----------|------|
| authorize 反复 302 到主系统登录页 | 浏览器没有 `INKHUB_TOKEN` cookie，或登录态已过期 | 先在主系统登录；确认是整页导航（不是 XHR） |
| `400 client_id 未登记` | `ac_partner` 无该 `app_key`；拼错；清库后种子未跑 | 查种子是否加载、重启主系统；SQL 核对 |
| `400 redirect_uri 未登记`（authorize） | 与白名单不是全串相等（本地/内网 host 有免登记豁免，不走此闸） | 用浏览器地址栏里的**原始串**逐字符对比（端口/尾斜杠/大小写/编码） |
| token `invalid_client` | secret 不匹配；yml 种子回写了新 secret；环境变量为空 | 对齐 `ac_partner.app_secret` 与子系统配置；检查 `*_CLIENT_SECRET` |
| token `invalid_grant` | code 已用过/超过 300 秒；回调并发消费；Redis 重启 | 重新走 authorize；保证回调只消费一次 |
| token `invalid_grant`（redirect_uri） | authorize 与 token 传的串不同 | 两处用同一配置项 `my.oauth2.client.redirect-uri` |
| token `server_error` | 主系统 `IDENTITY_IDP_PRIVATE_KEY` 未配置 | 按 §三 注入并重启 |
| JWKS 503 | 同上 | 同上 |
| 子系统启动失败：`my.idp.bearer.enabled=true 但未配置钥来源` | 只开了开关 | 补 `server-base-url` 或 `public-key-base64` |
| 验签恒失败 | `server-base-url` 少了 context path（漏 `/inkhub`）；网络不通；换钥后缓存未跟上 | `curl {base}/web/oauth2/jwks.json` 自检；重启子系统立即刷新 |
| `tenant_ids` 为空 | 用户没有 `ac_user_tenant` 行；或主系统是 `open` 模式 | 主系统补归属；共享编排已内置零租户闸（§4.6），自建链须自行 fail-closed |
| 零租户用户（含平台管理员）全部被拒 | `my.authz.client.enabled` 未开（无 `AuthzCheckApi`，零租户回判恒 false）；或 `server-base-url`/`app-key`/`app-secret` 缺失导致 `/authz/check` 调用失败 | 三个 `enabled` 逐个核对；补 `my.authz.client.*` 后重启；日志搜"管理员判定"看 provider 是否在位 |
| 注入不到 `SubsystemSessionService` | 少声明 `identity-mirror`（client 对它是 optional 不传递）；或宿主没把三个镜像 repo 扫成 bean；或扫描面不含 `com.bidr` 导致 `TokenServiceImpl` 未注册 | pom 补第二行依赖；核对 `idmUserMirrorService`/`idmUserTenantMirrorService`/`idmTenantMirrorService` bean 名与 `kernel.scan.base-packages` |
| 主系统（墨枢）里也出现 `SubsystemSessionService` | 不应发生：主系统无镜像，条件装配退位 | 查主系统 classpath 是否被别的包带进 `identity-mirror`（应 0 条） |
| 主系统换钥后突然全员登录失败 | 子系统是静态公钥形态，或动态缓存未过期 | 更新静态公钥；重启子系统清缓存 |
| 授权页 URL 拼好了但报 `unsupported_response_type` | `response_type` 不是 `code` | 检查直链模板 |

---

## 六、安全与运维纪律

1. **私钥不出主系统**；公钥可公开。子系统任何位置出现私钥都按泄露处理。
2. **`client_secret` 只放后端**，走环境变量；不要写进前端配置、日志、Git。
3. `redirect_uri` **全串精确**：不要用通配/前缀/origin 思维；本地联调与生产各登记一行。
4. **受众闸（`app_id`）是强制项**：IdP 用同一把私钥给所有接入方签票，只验签名会让"签给 A 子系统的票"被 B 收下。`IdpTokenVerifier` / `IdpBearerVerifier` 在配置了期望接入方时，要求票面 `app_id` **全串相等、缺失同样拒**；期望值＝`my.idp.bearer.app-id`，缺省回退 `my.oauth2.client.client-id`，两者都空且 `enabled=true` ⇒ 启动失败。自己实现验签（非 bidr 栈）时同样要校验这一条。
5. `state` 必须动态生成并校验；不要复用；回调后立即失效。
6. **NTP 同步**：JWT `exp` 校验有 30 秒时钟偏移容忍；跨机部署两侧都做时间同步。
7. token TTL 默认 3600 秒、code TTL 300 秒；不要按旧 ticket 口径（90 秒）设计。
8. 日志只记摘要/指纹，不记 token 原文、不记 `client_secret`。
9. 子系统**不得写主系统的 `ac_*` 表**：身份/租户镜像写自己的库，主系统数据只经 open-api 只读同步。
10. 换钥＝全员重登（§3.5）；上线/换钥前确认所有子系统的取钥形态（动态/静态）。

---

## 七、参考实现：epc_ai

| 文件 | 作用 |
|------|------|
| `server/epc-ai-common/src/main/java/com/bidr/epc/ai/inkhub/InkHubOAuth2LoginController.java` | `POST /web/epc-ai/oauth2/exchange`：code → `OAuth2Client.exchangeCode` → `IdpTokenVerifier.verify` → 镜像同步（best-effort）→ 装配 `IdmHolder` → `BidrLoginService.login()` → `finally` 清线程态 |
| `.../service/auth/BidrLoginService.java` | 适配层（60 行）：取 `IdmHolder` 用户名 → `SubsystemSessionService.login(username)`；`refresh(token)` 直接委托。登录编排零复制 |
| `.../inkhub/EmbeddedEntryController.java` | 内嵌入口：验票 → 拉租户 → 镜像同步 → 零租户闸（非管理员拒绝）；`isSystemLevel(principal)` 委托 `SubsystemSessionService#isPlatformAdmin(uid)`（`/authz/check` 回判，不看 uid 数值） |
| `.../InkHubApiClient.java` | `/open-api/v1/tenants`（Bearer）与 `/open-api/v1/projects`（x-api-key）的 HTTP 客户端；统一信封解析、UTF-8 显式解码 |
| `.../InkHubTenantSyncService.java` | 写本地 `idm_*` 用户/租户镜像（唯一账号源）；只做同步，不做登录闸 |
| `view/.env.development` | `VITE_ssoDomain` + `VITE_ssoLoginUrl`（authorize 直链） |

epc 的关键取舍：**B 形态**（前端回跳取 code → POST 后端 exchange）；租户镜像**best-effort 降级**（同步失败不阻断存量账号，新账号由共享编排的镜像闸拒绝）；登录/刷新/租户闸/管理员回判**全部复用 core `SubsystemSessionService`**，epc 侧只剩"身份从哪来"；日志只打 token 的 MD5 前 12 位指纹。

---

## 八、现状与未覆盖（诚实标注）

**当前成立（源码事实，2026-11-04）**：

- 三端点 authorize / token / jwks + 授权码单次有效（300s）+ RS256 + JWKS 公钥发布；
- `authorization-client` 的 `OAuth2Client` / `IdpTokenVerifier` / `IdpBearerVerifier` / `JwksKeyProvider`（动态 JWKS、1h 缓存、单飞、上次快照兜底、静态兜底、启动不依赖主系统）；
- `my.idp.bearer.enabled=true` 无钥来源 ⇒ 启动 fail-fast；且无期望接入方（`app-id`／`client-id` 都空）同样 fail-fast；
- **受众闸**：`IdpTokenVerifier` / `IdpBearerVerifier` 要求票面 `app_id` 与期望接入方全串相等，缺失/不符一律拒（跨子系统票混淆防线）；
- partner 种子（`PartnerSeeder`）、角色管理 API、全串白名单、`utf8mb4_bin` 列；
- **共享会话编排已下沉 core**（2026-11-04）：`authorization-client` 的 `SubsystemSessionService`＋条件自动装配（镜像闸 → 租户闸 → 零租户 `/authz/check` 管理员回判 → 双票签发）；epc 侧只剩 `BidrLoginService` 适配层与镜像同步；
- 主系统退位已验证（产物级）：主 fat jar 不含 `identity-mirror`、运行日志 0 次 `SubsystemSessionService`；
- `authorization-client` 离线测试 **86 腿全绿**（其中本链新增 23 腿：服务 19＋条件装配 4）；
- epc_ai 已按本文链路在联调环境落地：OAuth2 受众闸 E2E **11/11 通过**（含管理员零租户刷新经 `/authz/check`、跨子系统票拒收两腿；脚本在本地 `.qoder/dev-logs/20261104-audience-gate-e2e/` 下，同目录留档的只有 6/6 互投日志、11 腿那轮输出未持久化；均未入库）；真实浏览器全链路由用户手测。

**已知缺口 / 待办**：

1. `project-setup` skill 的 `gen_idp_keypair()` 在 Node 24 上不可用（§3.2）；修复前只能用手工命令。**修复该脚本是对接前置事项**。
2. `ac_partner.status` 没有被 OAuth2/open-api 校验（§1.2 第 4 条）；"停用"不是断接手段。
3. 没有回调白名单的管理 API/页面（截至本快照只有种子与 SQL）。
4. 密钥轮换是手工流程；无自动轮换、无多钥并存运维面（JWKS 已按 `kid` 选钥，结构上为多钥留了口）。
5. `IdpPrincipal` 不含 `tenant_ids`；租户必须走 `/open-api/v1/tenants`（或自行解 claims）。
6. 子系统依赖仍是**两个 jar**（`authorization-client` + `identity-mirror`）；空壳聚合 starter 未做（user 裁定"不急"）。
7. 本指南未覆盖：SPI 登录口 `/web/sso/login`（见 [SSO接入指南.md](SSO接入指南.md)）、主系统内部登录链、开放模式（`register-mode=open`）的细节（见 [Auth对接指南.md](Auth对接指南.md) §二）。
