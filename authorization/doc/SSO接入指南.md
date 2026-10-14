# SSO 接入指南（core/authorization）

> M15 棒 0／0b 下沉 core 的单点登录能力（签发、回查、出票、验票）。适用范围＝以本 core 为底座的**所有部署形态**（bidr 形态、plsintec 形态、子系统自接 IdP／接墨枢出票面形态）。
> 一句话：core 只定义"从当前请求上下文解析出已验签用户名"这一契约并自带签发口与回查口，**自身不感知任何具体 IdP**；各形态在自己一侧提供一枚 SPI bean 即可点亮。

---

## 一、为什么要下沉到 core

底座要当所有子系统的 SSO + 权限提供者，前提是**签发口在 core 而不是 inkhub-bidr**。plsintec 形态只基 core、不编 bidr 侧，此前该口在 plsintec 下 404。SSO 属通用能力，故归 auth 模块（`com.bidr.authorization`），随 core 一起被所有形态继承。

本模块内涉及的文件（均为纯加法，既有文件零修改）：

| 角色 | 类 | 路径 |
|------|----|------|
| 身份源 SPI（core 锚点） | `SsoUserHandler` | `service/login/SsoUserHandler.java` |
| 签发口 Controller | `SsoLoginController` | `controller/login/SsoLoginController.java` |
| 签发编排 Service | `SsoLoginServiceImpl` | `service/login/impl/SsoLoginServiceImpl.java` |
| 回查口 Controller | `TokenIntrospectController` | `controller/login/TokenIntrospectController.java` |
| 回查判定 Service | `SsoTokenIntrospectService` | `service/login/SsoTokenIntrospectService.java` |
| 回查出参 VO | `TokenIntrospectRes` | `vo/login/TokenIntrospectRes.java` |
| bidr 形态 SPI 实现（示例） | `IdmSsoUserHandler` | `framework/bidr-framework/.../authorization/service/IdmSsoUserHandler.java` |

---

## 二、两个端点速览

| 端点 | 方法 | 入参 | 出参 | 鉴权注解 |
|------|------|------|------|----------|
| `/web/sso/login` | POST | `id_token`（形参，仅形状兼容，不参与解析） | `LoginRes` | `@Auth(AuthNone.class)` |
| `/web/sso/introspect` | POST | `TokenReq { token }`（body，`@Validated`）＋请求头 `Authorization` | `TokenIntrospectRes` | `@Auth(AuthNone.class)` |

两个口都标 `@AuthNone`＝**不要求调用方先登录**。区别在于：`login` 靠 SPI 解析身份后签发登录态；`introspect` 是"人人可查三态、身份明细需带登录态"的分层回查（见 §四）。

---

## 三、签发口 `POST /web/sso/login`

### 3.1 身份来源
真实身份**不**取自 `id_token` 形参，而取自 `SsoUserHandler.resolveUserName()` 的线程态（见 §五）。`id_token` 形参仅为与既有 bidr 形态 SSO 口契约形状兼容而保留，缺失或为空不影响判定。

### 3.2 出参 `LoginRes`
JSON 可见字段：`customerNumber`、`name`、`avtar`、`accessToken`、`refreshToken`（`userId` 标了 `@JsonIgnore`，不出体）。与 `/web/login` 出参同形。收尾 `afterLogin` 与框架 `LoginController.afterLogin` 行为等价：跑 `fillToken` 链（`LoginFillTokenInf` SPI，`@Autowired(required=false)`）＋打登录留痕日志。

### 3.3 三态语义（成功侧＝三分支，与 `BidrLoginService.login()` 逐支等价）
解析出非空 `userName` 后，按 `ac_user.user_name` 口径落身份主体：

1. `ac_user` 命中 → `loginService.login(acUser)`
2. `ac_user` 未命中但 `ac_account` 主数据命中 → `loginService.loginOrReg(account)`
3. 均无 → `createUserService.createUser(userName, MD5(userName))` 并置 `UserTypeDict.SSO` 后 → `login`

> 🔴 **身份主体＝`ac_user`（不是 `ac_account`）**。`ac_account` 只是主数据来源，签发后的登录态一律挂在 `ac_user` 上。

### 3.4 拒绝侧（四态互斥，文案即根因、互不串台）
SPI 取用走 `ObjectProvider<SsoUserHandler>.getIfAvailable()`，三段拒绝均为 `ErrCodeSys.SYS_ERR_MSG` 口径：

| 态 | 触发条件 | 文案要点 |
|----|----------|----------|
| 未接入 | 无候选 bean（`getIfAvailable()` 返 null） | "未接入SSO身份源（SsoUserHandler 实现未注册）" |
| 解析不出 | handler 在但 `resolveUserName()` 返回 null/空白 | "SSO身份源未返回用户身份（id_token 缺失或未通过上游验签）" |
| 实现冲突 | 多候选且无 `@Primary` 消歧（`NoUniqueBeanDefinitionException`） | "SsoUserHandler 实现冲突（存在多个且无 @Primary）" |
| 装配故障 | SPI bean 自身创建失败 / `resolveUserName()` 抛运行时异常 | **不转业务拒绝**，按框架口径上浮为系统错误 |

> 🔴 **窄捕获口径**：只有 `NoUniqueBeanDefinitionException` 转业务拒绝。把 SPI 自身装配故障伪装成"实现冲突"会掩盖真实根因，故**故意不接**。这条边界由 `SsoLoginServiceImplTest` 钉住。

---

## 四、回查口 `POST /web/sso/introspect`

入参复用现成 `TokenReq`（只一个字段 `token`，`@NotNull`）。判定逻辑抽在 `SsoTokenIntrospectService`（便于纯 Mockito 覆盖各腿）。

### 4.1 三态语义（对被查 token）
1. **坏 token 串**：`AuthTokenUtil.decode` 自身抛 `PA_PARAM_FORMAT`（"安全令牌格式错误"）业务拒绝，不捕获——属预期；**可解码但 type 段未知**（`TokenType.of` 命中不到枚举）同归此态，在 `verifyToken` 之前以 `PA_PARAM_FORMAT` 拒绝，杜绝取 `tokenType.name()` 的 NPE。
2. **可解析但无效**（不存在于服务端会话 / 游客态 / 已注销 / 归属不符）：`valid=false`，`tokenType` 仍给出。
3. **有效登录态**：`valid=true`。

### 4.2 分层鉴据（已拍板）
- **三态判定人人可查**：`valid`、`tokenType` 任何调用方都能拿到。
- **身份明细需调用方自身带有效登录态**：`customerNumber / userId / name / expireSeconds` 仅当"被查 token 有效 **且** 调用方自己也登录"时才填充，否则留空。归属用户在 `ac_user` 查不到时留空不报错。

> 🔴 **`@AuthNone` 口不自动建立 token 线程上下文**，所以调用方登录态不能依赖线程态，必须手抽请求头：`AuthTokenUtil.extractToken(request)` 读的是 `RequestConst.TOKEN`，其值为 **`Authorization`**。抽/验失败仅当抛 `ServiceException`（业务拒绝，如脏串非三段）时降级为"未登录"、三态判定照常返回；其余异常照常上浮。

### 4.3 🔴 verifyToken 不分令牌类型——消费方须自行加口径
`verifyToken` 只判"是否是服务端认可的有效会话"，**不区分令牌类型**：refresh token、app token 等只要会话存在同样返回 `valid=true`。因此：

- 想拿"这是一枚有效的**网页访问令牌**"这一语义，消费方必须**自己判 `tokenType == WEB_ACCESS_TOKEN`**（`TokenType` 枚举名），单看 `valid` 不够。
- `tokenType` 出参给的是 `TokenType` 枚举名字符串，取值域：`WEB_ACCESS_TOKEN` / `WEB_REFRESH_TOKEN` / `APP_ACCESS_TOKEN` / `APP_REFRESH_TOKEN` / `PLATFORM_TOKEN` / `WECHAT_TOKEN` / `CHANGE_PWD_TOKEN` / `GUEST_TOKEN` / `IOT_TOKEN`。

### 4.4 expireSeconds 口径
Redis 写侧接口不读 TTL，故剩余寿命按会话 map 的 `TIMESTAMP` + `EXPIRED` 推算（口径与 `TokenServiceImpl` 续期同源），无法计算时留空。命中身份明细时打留痕日志：调用方身份 + 被查 token 前 8 位（不落全量 token）+ 命中用户。

---

## 五、`SsoUserHandler` SPI——各部署形态怎么提供 bean

core 侧只有这一枚接口：

```java
public interface SsoUserHandler {
    /** 解析当前 SSO 上下文中的用户名（对应 ac_user.user_name 口径）；null/空白视为未解析 */
    String resolveUserName();
}
```

**🔴 全应用至多一个生效实现。** 各形态按下表在自己一侧提供 bean：

| 形态 | 是否提供 bean | 提供方式 |
|------|--------------|----------|
| **bidr**（有 idm） | 是 | `IdmSsoUserHandler`：`@Service` + `@ConditionalOnClass(name="com.bidr.idm.config.IdmHolder")`，`resolveUserName()` 取 `IdmHolder.getUser()` 的用户名。idm starter 不在 classpath 时该 bean 不注册，SSO 口自动走"未接入"拒绝、不阻断启动。 |
| **子系统自接其它 IdP** | 是（自写） | 实现 `SsoUserHandler`，`resolveUserName()` 从自接 IdP 的验签后线程态返回用户名，`@Service` 注册即可。 |
| **子系统接墨枢出票面**（自读票，棒 0b 新增） | 是（core 内置） | `SsoJwtUserHandler`：`@Service` ＋ `@ConditionalOnProperty(name="sso.jwt.public-key")`——**配了公钥才出生**。子系统实例配 `sso.jwt.public-key`（墨枢公钥，X.509 base64 单行）即自动装配；`resolveUserName()` 从当前请求读 `id_token`（先请求参数、空白回落同名 header，照 IdaaS 拦截器口径），用该公钥本地验签取 `username`，全链路零 idm、零业务包依赖。不配该键＝bean 不注册，SSO 登录口走"未接入"拒绝、不阻断启动。🔴 `@ConditionalOnProperty` 不带 `havingValue`＝**键存在即出生**（值配空串也算存在）；**不用** `@ConditionalOnMissingBean`（后者赌组件扫描顺序，不可靠）。 |
| **plsintec**（无 idm） | 否 | 不提供 bean；SSO 登录口按"未接入SSO身份源"业务拒绝，不影响启动。 |

**取用规则**（`SsoLoginServiceImpl.resolveHandler()`）：
- 一律用 `ObjectProvider.getIfAvailable()`——null＝未接入、`NoUniqueBeanDefinitionException`＝冲突。
- **为什么不用 `orderedStream()`**：本机 Spring 5.3 实测 `orderedStream()` 多候选时**不**按 `@Primary` 去重（size 仍为 2），会把"已配 `@Primary` 消歧"的合法配置一并拒掉；`getIfAvailable()` 认 `@Primary`。
- 注册多个实现时须其一标 `@Primary`（或保证仅一个生效），否则走"实现冲突"拒绝。

> 🔴 **墨枢自身永不声明 `sso.jwt.public-key`**——该键只属于接入方（子系统）。误配的后果分两形态：**bidr 形态**（classpath 另有 `IdmSsoUserHandler`）＝双 handler 撞 `getIfAvailable()` 的"实现冲突"拒绝（业务可见、不崩启动，这条误配态被当验收腿保留，不静默兜）；**plsintec／子系统形态**（只有一只 handler）＝**没有任何冲突信号**，handler 静默生效，"墨枢自签的票"就成了打开墨枢本账号的通道。所以纪律是"完全不声明该键"，不是"声明成空串"。机器针＝主仓 `SsoConfigSurfaceConventionTest`（射程＝主仓四档 yml 的字面声明）。

> 依赖方向 framework → core 不变：core 不感知 idm，`IdmSsoUserHandler` 是 bidr 侧唯一的粘合点。子系统禁止 `import com.bidr.framework.*`，只依赖 core 的 `SsoUserHandler` 锚点。

---

## 六、接入纪律

1. **独立 bean 名 + 独立路径**：两个 Controller 用带前缀的 bean 名（`SsoLoginController`、`TokenIntrospectController`）和 `/web/sso/*` 路径，**不接管、不继承** `/web/auth/login`。同名双 controller 靠组件扫描覆写顺序定胜负＝不可靠机制，设计即绕开。
2. **子系统 auth 取舍**：SSO 能力随 core 默认可用；子系统若不需要对外签发，可只用回查/校验侧（`introspect`、`verifyToken`、上下文注入、空间隔离），把签发口当作"存在但不入口"。具体裁剪口径见 `docs/立项-M15-平台化架构.md`。
3. **不改既有行为**：本模块 SSO 全部为纯加法，既有文件零修改；`login` 三分支与既有 bidr 形态逐支等价。
4. 🔴 **`/web/sso/*` 不进 `bidr.idm.security-path`**：idm 验签拦截器只守该路径清单（现值 `/web/auth/login`），把出票/签发口加进去＝挂到 idm 验签下，与"core 不感知 idm"直接冲突。bidr 形态下本组口无 Idm 前置，`IdmHolder` 空 ⇒ 走"未接入SSO身份源"业务拒绝＝**预期降级，不是故障**（真机已验）。
5. 🔴 **接入方前端 `VITE_ssoDomain` 必填**：缺省时框架推导出的 `/sso` 落点无人服务（404）。出票方（IdP）侧需要一张中转页（墨枢＝`#/sso/authorize`，参与登录守卫、页面自查登录态分流）；**验票方（子系统）侧不需要任何中转页**。
6. 🔴 **"登录后还要做的附加动作"＝加 `LoginFillTokenInf` fillToken bean，不写 controller**：core `afterLogin()` 已遍历该 SPI（`SpaceLoginTokenFill` 是现成实现）。子系统只是换了身份解析段，登录后段本来就是继承的。
7. 🔴 **回调地址口径**：白名单只收**不带裸 query 的地址**，跳转时整串百分号编码——`&` 不编码会被截断，票挂在 query 上、再带裸 query 必丢参；出票/中转页的回调地址入参名＝**`sso_redirect_uri`**，不得叫 `redirect_uri`（与出票口服务端参数名及 OAuth 习惯名撞车、易误传）。命中判定＝**全串等值**（列 collation `utf8mb4_bin`），禁前缀/like，防子域延伸绕过。
8. 🔴 **两侧 NTP 同步，时钟偏移预算 <30 秒**：票 `exp = 签发 + ttl`（部署键 `sso.ticket.ttl-seconds`，部署档 60；core 不写兜底默认、缺席即拒；硬上界 900 秒——无 jti 吊销且票走 URL 参数可从浏览器历史/访问日志捞到，误填毫秒口径＝发不可撤的长命凭证）。验签侧 exp 判定含 30 秒偏移容忍（跨机 NTP 漂移不得把刚签出的票判死）⇒ **实际最大存活＝ttl + 30 秒**（60 档即 **90 秒**）。对外文案与风险评估一律按 90 秒口径。

---

## 七、最小调用样例（凭据用占位符）

```bash
# 1) 签发：需当前请求上下文已被形态的 SPI 解析出用户名（bidr 形态＝idm 已验签）
curl -X POST http://<host>/<ctx>/web/sso/login

# 2) 回查（未登录，只拿三态）
curl -X POST http://<host>/<ctx>/web/sso/introspect \
     -H 'Content-Type: application/json' \
     -d '{"token":"<待查token>"}'
#   → 只填 valid / tokenType；要判"有效网页访问令牌"需自行加 tokenType==WEB_ACCESS_TOKEN

# 3) 回查（调用方带自己的登录态，才拿身份明细）
curl -X POST http://<host>/<ctx>/web/sso/introspect \
     -H 'Content-Type: application/json' \
     -H 'Authorization: <调用方自己的token>' \
     -d '{"token":"<待查token>"}'
#   → 被查 token 有效且调用方已登录时，补 customerNumber/userId/name/expireSeconds
```

---

## 八、子系统接入（接墨枢出票面）

目标形态：子系统登录改走"墨枢签票 → 本地验票换本系统会话"，**后端零 Java 改动、前端只改两处配置＋一行 api path**。

### 8.1 三处配置

| # | 侧 | 配置 | 说明 |
|---|----|------|------|
| ① | 子系统后端 | `sso.jwt.public-key=<墨枢公钥单行 base64>` | 验票公钥（X.509）。配了才出生内置 handler（§五 形态三）。公钥由墨枢方**线下交付**（本项目现值备在 `sso-key/pub-oneline.txt`，392 字节单行；私钥永不出墨枢 `server/.env`）。 |
| ② | 子系统前端 | `VITE_ssoDomain=<墨枢前端域名>` | 框架拼墨枢 authorize 中转页跳转地址用；不配＝跳转落点 404（§六 第 5 条）。 |
| ③ | 子系统前端 | login api path 指向本实例 `/web/sso/login` | 票换会话走的就是 core 签发口（§三）。框架 `apis/login/login.ts` 的 `ssoLogin` 即该形态，子系统把它从 `/auth/login` 改指本实例即可。 |

### 8.2 一条登记 SQL（白名单出厂为空＝默认全拒，谁要接入谁先插行）

```sql
-- app_id 反查 partner：SELECT id FROM ac_partner WHERE app_key = '<子系统 app_id>';
INSERT INTO ac_sso_redirect (partner_id, redirect_uri, remark, create_by, create_at, valid)
VALUES (<partner_id>, '<回调地址全串，不带裸 query>', '<用途/环境说明>', '<登记人>', NOW(), '1');
```

- 表 `ac_sso_redirect` 由 core 启动期自动建（`MybatisPlusConfig.initTables()` 收容器内全部 Schema bean），无需手工 DDL。
- 🔴 `(partner_id, redirect_uri)` **普通索引、故意非 UNIQUE**＋`valid` 软删位（§六 第 7 条等值口径）：撤销登记＝置 `valid='0'`，再登记＝插新行。
- 管理入口两选一：直接跑该 SQL；或墨枢 partner 管理页（Channel）的"回调地址"登记区（Portal 表 `AcSsoRedirect`，需先把 Channel 页挂进菜单）。

### 8.3 出票调用样例（凭据用占位符）

```bash
# 必须带墨枢自身的有效登录态（本口未标免登录豁免；未登录＝"登录信息已过期"拒绝，且不透出白名单查询）
curl -X POST 'http://<墨枢host>/<ctx>/web/sso/ticket?app_id=<子系统app_id>&redirect_uri=<百分号编码后的回调地址>' \
     -H 'Authorization: <墨枢登录token>'
# → {"status":{"code":0},"payload":{"idToken":"<RS256票>","redirectUri":"<服务端回填的白名单原文>"}}
#   票 claims 只 username+exp；ttl 60 档实际最长存活 90 秒（§六 第 8 条）。跳转只用服务端回填的 redirectUri。
```

墨枢前端中转页（`#/sso/authorize`）已完成上述编排：读 `app_id`＋`sso_redirect_uri` → 出票 → `location.href = 回调原文 + '?id_token=' + 票`。子系统落地后走框架现成链路：读 query `id_token` → 剥参 → 调本实例 `/web/sso/login` 换登录态。`app_id` 是浏览器跳转里的**自报公开值**——组合判据把授权面收回"自家那几条地址"，但它不是身份凭据；"这张票能被谁消费"的终局判据（`aud` claim）本批未做、已登记待拍（Q8）。

### 8.4 回调页归属

飞书等第三方 IdP 的回调页天生是**宿主项目自己的件**：框架只给后端两口（签发 `/web/sso/login`、出票 `/web/sso/ticket`）。pan 项目那张在 `D:/workspace/plsintec/pan/view/src/views/pan/FeishuCallback.vue`，不在框架仓；墨枢自己要飞书登录才需要自建一张（本批未做）。

---

## 九、证据与实测现状（诚实标注）

**棒 0（SSO 下沉）——五腿全绿**：① plsintec 两步编译（fat jar 316 个 lib 中 `bidr-framework`/`idm-starter`/`bidr-mdm`/`bidr-notice` 全 0、`authorization`=1＝底座边界的产物级证据）；② bidr/dev 形态容器装配；③ 两口 HTTP 端到端九腿；④ `@ConditionalOnClass` 真实扫描（以拒绝文案做两形态差异判据）；⑤ plsintec 形态容器启动。

**棒 0b（出票＋验票）——真机 E2E 全绿**（正源＝`.qoder/dev-logs/20261014-094847-M15-SSO出票与子系统验签/e2e-report.md`）：墨枢侧 24 腿（登记三拒/组合判据四拒法/出票/跨语言验签/撤销→拒→再登记→恢复闭环）；建表与 collation 腿（`ac_sso_redirect` 启动自建、`redirect_uri` 列 `utf8mb4_bin`、PAD SPACE 实证）；子系统验票 V1~V4（epc_ai 8081 真跑：真票换会话、篡改签名段拒、缺票拒、过期重放拒，30 秒容忍窗两侧观测）；误配公钥＝"实现冲突"真机复现；浏览器整腿 B1（未登录直达 authorize 保参落登录页→登录后自动出票回跳，改拍后绿）＋B2（登录态直达出票，`redirectUri` 为服务端回填的白名单原文）。

**离线证据（已绿）**：authorization 模块定向 62 腿＋业务仓不带旗标全量 1336/0/Skipped 35（棒 0b 收口重跑）。

🔴 **未跑腿（不得写成已成立）**：① partner 页同屏登记区的**浏览器 UI 腿**（M-20 前半：`ac_menu` 无 Channel 行、动态路由不可达；插菜单＝改共享库可见状态、dev=pre 同实例，未点名未插；API 侧两腿已由 A6-① 覆盖，UI 交手测）；② 角色闸"非 admin"真机腿（本机无第二账号，只有离线分支腿）；③ "错公钥 ⇒ 子系统拒票"真机腿（离线单测覆盖，真机只做了等价的篡改签名段拒法）。
