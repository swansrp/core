# SSO 接入指南（core/authorization）

> 本文只讲**一条**通道：SPI 登录口 `POST /web/sso/login` + `SsoUserHandler` 身份源。核心语义＝"从当前请求上下文解析出已验签用户名，凭它铸本系统登录态"。
>
> 🔴 **两份配套文档，别混读**：
> - 想知道"我该走 SPI 还是走 OAuth2"⇒ 见 [Auth对接指南.md](Auth对接指南.md) §一 能力地图与通道选型。
> - 想接**标准的 OAuth2 授权码 + JWKS 验签**（跨应用/跨域子系统）⇒ 见 [子系统SSO对接指南.md](子系统SSO对接指南.md)。
>
> 🔴 **本文已于 T1.3 棒8 全面收敛**：旧版的 `POST /web/sso/introspect`（回查口）、`POST /web/sso/ticket`（出票口）、`SsoJwtUserHandler`／`SsoJwtUtil`／`SsoConfigKeys`（自读票链路）、`sso.jwt.public-key`（配置键）、前端 `#/sso/authorize`（墨枢中转页）**整族退役、无回滚**。这些类与端点已从代码中删除，本文不再保留其任何说明；仓内若见到残留提及，只可能出现在历史注释或守卫测试里。

---

## 一、这条通道解决什么问题

场景：**宿主应用自己有一个 IdP**（或本身就是上游登录体系的一部分），已经把用户验过签了，只想问 core 要一张本系统的登录态。

典型形态：

| 形态 | 身份从哪来 | 谁提供 bean |
|------|-----------|------------|
| bidr 形态（有 idm） | idm 验签后的线程态 | 框架侧 `IdmSsoUserHandler`（`@ConditionalOnClass` 保护） |
| 子系统自接别的 IdP（飞书、企业微信、CAS、自建 OIDC…） | 自接 IdP 验签后的线程态 | 子系统自写一枚 `SsoUserHandler` 实现 |
| plsintec 形态（无 idm） | 不提供 | 无 bean ⇒ 登录口按"未接入SSO身份源"业务拒绝，**不影响启动** |

它**不**解决：跨域浏览器跳转、授权码换 token、JWKS 验签——那是 OAuth2 通道的活。两者区别见 [Auth对接指南.md](Auth对接指南.md) §一。

---

## 二、端点契约

| 项 | 值 |
|----|----|
| 方法/路径 | `POST /web/sso/login` |
| 鉴权注解 | `@Auth(AuthNone.class)`＝**不要求调用方先登录** |
| 入参 | `id_token`（形参，**仅形状兼容**；真实身份不从这里取，缺失或为空不影响判定） |
| 出参 | `LoginRes`：`customerNumber`、`name`、`avtar`、`accessToken`、`refreshToken`（`userId` 标了 `@JsonIgnore` 不出体） |
| 收尾 | `afterLogin` 与框架 `LoginController.afterLogin` 行为等价：遍历 `LoginFillTokenInf` SPI（`@Autowired(required=false)`）＋打登录留痕日志 |

> 🔴 `id_token` 形参保留只为与既有 bidr 形态 SSO 口**契约形状**兼容。真实的身份来源**永远**是 `SsoUserHandler.resolveUserName()` 的返回值（见 §五）。

调用样例：

```bash
# 前置：当前请求上下文已被形态的 SPI 解析出用户名（bidr 形态＝idm 已验签）
curl -X POST http://<host>/<ctx>/web/sso/login
# → {"status":{"code":0},"payload":{"customerNumber":"...","name":"...","accessToken":"...","refreshToken":"..."}}
```

---

## 三、三分支：解析出用户名之后怎么落身份主体

`SsoLoginServiceImpl.issueFor(userName)`（被本通道与 `/web/auth/login` 的 `IdentityBridgeLoginService` 共用），按 `ac_user.user_name` 口径三分支：

1. **`ac_user` 命中** → `assertSsoTenantReady(acUser)` 过闸 → `loginService.login(acUser)`
2. **`ac_user` 未命中、`ac_account` 主数据命中** → `loginService.loginOrReg(account)` → 按 username 复核租户归属（`assertSsoTenantReady`）→ 返回
3. **均无** → `assertSsoAutoCreateAllowed()` 过闸 → `createUserService.createUser(userName, MD5(userName))` → 置 `UserTypeDict.SSO` → `loginService.login(newUser)`

> 🔴 **身份主体＝`ac_user`（不是 `ac_account`）**。`ac_account` 只是主数据来源，签发后的登录态一律挂在 `ac_user` 上。
>
> 🔴 **两枚租户闸**（`my.auth.register-mode` 驱动，见 [Auth对接指南.md](Auth对接指南.md) §二）：
> - `assertSsoAutoCreateAllowed()`：`tenant`/`closed` 模式下**禁止** SSO 自动建号，拒绝文案分别是
>   `"当前为租户模式，账号需由管理员/主系统创建并分配租户后使用"` / `"自助注册已关闭，账号请联系管理员创建"`。
> - `assertSsoTenantReady(acUser)`：`tenant` 模式下，`SSO`/`REGISTER` 类型的存量账号**必须**已有至少一条 `ac_user_tenant` 归属，否则拒绝
>   `"该账号尚未分配租户，请联系管理员分配后重新登录"`；`SYSTEM`/`ACCOUNT` 类型不查。
>
> `open` 模式（InkHub 默认）两闸均旁路＝全局账号形态。租户模式的完整语义见 [Auth对接指南.md](Auth对接指南.md) §二。

---

## 四、拒绝侧：四态互斥，文案即根因

SPI 取用走 `ObjectProvider<SsoUserHandler>.getIfAvailable()`，三段拒绝均为 `ErrCodeSys.SYS_ERR_MSG` 口径：

| 态 | 触发条件 | 文案要点 |
|----|----------|----------|
| 未接入 | 无候选 bean（`getIfAvailable()` 返 null） | "未接入SSO身份源（SsoUserHandler 实现未注册），无法完成单点登录" |
| 解析不出 | handler 在但 `resolveUserName()` 返回 null/空白 | "SSO身份源未返回用户身份（id_token 缺失或未通过上游验签），无法完成单点登录" |
| 实现冲突 | 多候选且无 `@Primary` 消歧（`NoUniqueBeanDefinitionException`） | "SsoUserHandler 实现冲突（存在多个且无 @Primary），无法完成单点登录" |
| 装配故障 | SPI bean 自身创建失败 / `resolveUserName()` 抛运行时异常 | **不转业务拒绝**，按框架口径上浮为系统错误 |

> 🔴 **窄捕获口径**：`resolveHandler()` 只捕获 `NoUniqueBeanDefinitionException` 转业务拒绝。把 SPI 自身装配故障伪装成"实现冲突"会掩盖真实根因，故**故意不接**。这条边界由 `SsoLoginServiceImplTest` 钉住。

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
| **plsintec**（无 idm） | 否 | 不提供 bean；SSO 登录口按"未接入SSO身份源"业务拒绝，不影响启动。 |

**取用规则**（`SsoLoginServiceImpl.resolveHandler()`）：

- 一律用 `ObjectProvider.getIfAvailable()`——null＝未接入、`NoUniqueBeanDefinitionException`＝冲突。
- **为什么不用 `orderedStream()`**：本机 Spring 5.3 实测 `orderedStream()` 多候选时**不**按 `@Primary` 去重（size 仍为 2），会把"已配 `@Primary` 消歧"的合法配置一并拒掉；`getIfAvailable()` 认 `@Primary`。
- 注册多个实现时须其一标 `@Primary`（或保证仅一个生效），否则走"实现冲突"拒绝。

> 🔴 **不再有 core 内置的 `SsoJwtUserHandler`**（旧版曾用 `sso.jwt.public-key` 配置点亮一只"自读墨枢票"的 handler）。T1.3 棒8 起跨系统形态改走 OAuth2 授权码 + JWKS（见 [子系统SSO对接指南.md](子系统SSO对接指南.md)），该 handler 连同其配置键整族删除。**旧文档/旧部署脚本里的 `sso.jwt.public-key` 是废键**——遇到就删，不再被任何代码读取。

> **依赖方向 framework → core 不变**：core 不感知 idm，`IdmSsoUserHandler` 是 bidr 侧唯一的粘合点。子系统禁止 `import com.bidr.framework.*`，只依赖 core 的 `SsoUserHandler` 锚点。

---

## 六、接入纪律

1. **独立 bean 名 + 独立路径**：`SsoLoginController` 用带前缀的 bean 名与 `/web/sso/*` 路径，**不接管、不继承** `/web/auth/login`。同名双 controller 靠组件扫描覆写顺序定胜负＝不可靠机制，设计即绕开。
2. **`/web/sso/*` 不进 `bidr.idm.security-path`**：idm 验签拦截器只守该路径清单（现值 `/web/auth/login`），把签发口加进去＝挂到 idm 验签下，与"core 不感知 idm"直接冲突。bidr 形态下本口无 Idm 前置，`IdmHolder` 空 ⇒ 走"未接入SSO身份源"业务拒绝＝**预期降级，不是故障**（真机已验）。
3. **"登录后还要做的附加动作"＝加 `LoginFillTokenInf` fillToken bean，不写 controller**：core `afterLogin()` 已遍历该 SPI（`SpaceLoginTokenFill` 是现成实现）。子系统只是换了身份解析段，登录后段本来就是继承的。
4. **不改既有行为**：本模块 SSO 为纯加法，既有文件零修改；`login` 三分支与既有 bidr 形态逐支等价。
5. **子系统若不需要对外签发**：可以只用 OAuth2 通道（`/web/oauth2/*`），把 SPI 签发口当作"存在但不入口"——不提供 `SsoUserHandler` bean 即可，登录口自动走"未接入"拒绝。

---

## 七、与 OAuth2 通道的取舍

| 维度 | SPI `/web/sso/login` | OAuth2 `/web/oauth2/authorize` + `/token` |
|------|---------------------|------------------------------------------|
| 身份怎么来 | 宿主 IdP 已验签的**线程态**（同一 JVM 内） | 跨域浏览器跳转 + 授权码换 token + JWKS 验签 |
| 需要什么 bean | 一枚 `SsoUserHandler` | 无需 bean；配 `my.oauth2.client.*` |
| 适用于 | 单进程/同应用内 SSO，bidr 形态 | 前后端分离的子系统、跨域、第三方宿主 |
| 租户闸 | `assertSsoAutoCreateAllowed` + `assertSsoTenantReady`（`tenant`/`closed` 生效） | authorize/token 只在票里带 `tenant_ids` 快照、**不拒绝零租户**；子系统侧闸：用 core 共享编排（`SubsystemSessionService`，自带零租户回 `/authz/check` 判管理员）或自行 fail-closed |
| 文档 | 本文 | [子系统SSO对接指南.md](子系统SSO对接指南.md) |

---

## 八、证据与实测现状（诚实标注）

**棒 0（SPI 下沉 core）——五腿全绿**：

1. plsintec 两步编译（fat jar 316 个 lib 中 `bidr-framework`/`idm-starter`/`bidr-mdm`/`bidr-notice` 全 0、`authorization`=1＝底座边界的产物级证据）；
2. bidr/dev 形态容器装配；
3. 两口 HTTP 端到端九腿（当时含 introspect，该口已退役；login 腿等价保留）；
4. `@ConditionalOnClass` 真实扫描（以拒绝文案做两形态差异判据）；
5. plsintec 形态容器启动。

**离线测试**（现役）：

| 测试 | 钉住什么 |
|------|---------|
| `SsoLoginServiceImplTest` | 三分支、四态拒绝、窄捕获边界（装配故障不伪装成冲突） |
| `SsoTenantModeGateTest` | `tenant`/`closed` 两闸的放行与拒绝文案 |
| `SsoLoginControllerTest` | 端点形状、`id_token` 形参不参与解析 |
| `IdentityBridgeLoginServiceTest` | `issueFor` 共用方的装配等价 |
| `AcSsoRedirectServiceTest` | OAuth2 侧 redirect 白名单（与本文无关，列出以免误认） |
| `SubsystemSessionServiceTest` / `SubsystemSessionAutoConfigurationTest` | OAuth2 侧共享会话编排（`authorization-client`）：镜像闸/租户闸/零租户管理员回判/refresh 票类型与一次性/条件装配与主系统退位（与本文无关，列出以免误认） |

🔴 **未跑腿（不得写成已成立）**：子系统自写 `SsoUserHandler` 的真机端到端腿（只有离线分支腿 + bidr 形态真机腿）。plsintec 形态下"未接入"拒绝的线上表现仅容器启动与日志观测，未做 HTTP 逐态验证。
