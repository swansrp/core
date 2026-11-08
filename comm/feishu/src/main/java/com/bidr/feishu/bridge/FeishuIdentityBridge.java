package com.bidr.feishu.bridge;

import com.bidr.feishu.config.OnFeishuBridgeForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.authorization.identity.IdentityBridge;
import com.bidr.authorization.identity.BridgeUserPrincipal;
import com.bidr.authorization.identity.IdentityLoginCmd;
import com.bidr.authorization.identity.OrgSyncResult;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.lark.oapi.Client;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReqBody;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenResp;
import com.lark.oapi.service.contact.v3.model.Department;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.contact.v3.model.GetUserResp;
import com.lark.oapi.service.contact.v3.model.User;
import com.bidr.feishu.config.FeishuProperties;
import com.bidr.feishu.constant.FeishuUrl;
import com.bidr.feishu.dto.sso.FeishuSsoUser;
import com.bidr.feishu.service.contact.FeishuDepartmentService;
import com.bidr.feishu.service.contact.FeishuUserService;
import com.bidr.feishu.util.FeishuMobileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: FeishuIdentityBridge
 * Description: 飞书身份桥（T1.1 棒5）——core SPI {@link IdentityBridge} 在飞书身份源形态的实现。
 * 与内核（core/authorization 的 IdentityBridgeLoginService/OrgSyncApplier/IdentitySyncJob）组成
 * "桥拉数、内核落库"的落库反转形态：本桥只产纯数据（{@link BridgeUserPrincipal}/{@link OrgSyncResult}），
 * 🔴 不写内核表（M-5：pack 禁 import core 写表 *Service，有静态 grep 闸）。
 *
 * <p>三类语义来源（自 FeishuSsoService/FeishuSyncService SPI 化，原类随棒5 退役）：</p>
 * <ul>
 *   <li><b>login</b>：原 FeishuSsoService.ssoLogin 的换身份半边——cmd.code（飞书 OAuth 回调票）→
 *       user_access_token→open_id→contact v3 反查 user_id（employee_id，管理员分配的英文工号），
 *       未分配/无字段权限回退 open_id；accountIdentity＝user_id‖open_id 即对齐键（与同步链路同一身份键）。
 *       原类的落库/建号半边（selectById 未命中即插 AcAccount）随收编剥离——内核凭 principal.username
 *       走 {@code SsoLoginServiceImpl.issueFor} 三分支铸态（M-1：桥不签产品 JWT，不落库）。
 *       取边勘误（IdentityBridge javadoc）：飞书桥取"桥内自解析票据"边——code 换身份全链在本桥完成，
 *       cmd 三态票据哪个非空用哪个（{@code credential()}），本形态实际消费 code 票。</li>
 *   <li><b>syncOrg</b>：原 FeishuSyncService.syncAll 的数据拉平面——飞书部门树（含根部门）＋逐部门
 *       拉平账号，映为 DeptRow/UserRow 纯数据；原 upsertDept/upsertAccount/createUserIfAbsent 落库半边
 *       退役（落库归内核 OrgSyncApplier 全量替换，建号走其 accountSyncUser 漏斗——棒3 的
 *       isTenantAssignable fail-closed 预检在漏斗侧已备）。</li>
 *   <li><b>authorizeUrl</b>：原 FeishuSsoService.getAuthorizeUrl 逐字平移——飞书网页授权页地址，
 *       state 为回跳 hash 路径原样透传（原样编入授权请求、回调原样带回，不解释）。</li>
 * </ul>
 *
 * <p><b>onStartup 不覆盖</b>：原独立仓链无启动期组织初始化语义（对照 bidr 侧 DeptInitService
 * 启动即拉——飞书侧仅 cron 定时同步），SPI 默认空体即可；identity.org-sync.enabled=true 时启动期的
 * 拉平落库由内核 IdentitySyncJob.onStartup 的 runOnce("startup") 承担，与本桥钩子无关。</p>
 *
 * <p><b>状态映射口径</b>：飞书 isActivated/isResigned/isExited 三布尔在原链收敛为单一布尔
 * （active＝已激活且非离职非退出，其余全量停用）。SPI UserRow.status 三值下本桥映射
 * 在职→active、其余（未激活/离职/退出）→frozen——frozen 使内核落库腿置停用（selectActive
 * 建号链不捞起），与原链"离职不建号"的漏斗行为逐字等价；resigned 值本桥不产出（飞书侧的
 * 离职/退出在原链即无独立语义出口，SPI 的 resigned＝在职停用腿，与原链停用口径冲突，登记于棒5 报告）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@Conditional(OnFeishuBridgeForm.class)
public class FeishuIdentityBridge implements IdentityBridge {

    /** 在职（原同步链 employStatus=YES 的语义） */
    private static final String STATUS_ACTIVE = "active";
    /** 停用（未激活/离职/退出统一收敛——见类注释状态映射口径） */
    private static final String STATUS_FROZEN = "frozen";

    private final FeishuProperties feishuProperties;
    private final Client feishuClient;
    private final FeishuDepartmentService feishuDepartmentService;
    private final FeishuUserService feishuUserService;

    /**
     * 飞书网页授权页地址（原 getAuthorizeUrl 逐字平移，语义与对外 /web/feishu/sso/authorize 契约不变）
     *
     * @param state 回跳来路（hash 内部路径，如 /pan?folderId=3）：飞书授权后原样回带，
     *              nginx 将回跳 query 透传进前端 hash 路由，回调页据此恢复原页面；
     *              空则保留 pan 标识（飞书 state 上限 128 字节，超长由前端改走 sessionStorage 兜底）
     */
    @Override
    public String authorizeUrl(String state) {
        try {
            String stateParam = FuncUtil.isEmpty(state) ? "pan"
                    : URLEncoder.encode(state, StandardCharsets.UTF_8.name());
            return feishuProperties.getHost() + FeishuUrl.AUTHORIZE_PAGE
                    + "?app_id=" + feishuProperties.getAppId()
                    + "&redirect_uri=" + URLEncoder.encode(feishuProperties.getRedirectUri(),
                    StandardCharsets.UTF_8.name())
                    + "&state=" + stateParam;
        } catch (UnsupportedEncodingException e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "构建飞书授权地址失败");
        }
    }

    /**
     * 票据换身份（桥内自解析边）：code → user_access_token → open_id → 反查 user_id 定对齐键。
     * 验票失败（SDK 异常/飞书侧拒绝/票据空白）抛 {@link ServiceException} 运行时异常，由内核
     * IdentityBridgeLoginService 统一兜底上浮（core SPI 契约：不定义受检异常）。
     */
    @Override
    public BridgeUserPrincipal login(IdentityLoginCmd cmd) {
        String code = cmd == null ? null : cmd.credential();
        Validator.assertNotBlank(code, ErrCodeSys.SYS_ERR_MSG, "飞书授权码不能为空");
        FeishuSsoUser feishuUser = getUserByCode(code);
        // 账号身份键统一为飞书 user_id（与同步链路一致），取不到回退 open_id
        String resolved = resolveUserName(feishuUser.getOpenId());
        String identity = FuncUtil.isEmpty(resolved) ? feishuUser.getOpenId() : resolved;
        return buildPrincipal(feishuUser, identity);
    }

    /**
     * 全量组织同步（拉数半边）：部门树（含根部门行）＋逐部门账号一次拉平，映为纯数据；
     * 落库由内核 OrgSyncApplier 统一执行（全量替换＋空腿护栏），本桥不写内核表。
     */
    @Override
    public OrgSyncResult syncOrg() {
        OrgSyncResult result = new OrgSyncResult();
        result.syncedAt = new Date();
        result.depts = new ArrayList<>();
        result.users = new ArrayList<>();
        // 根部门行（原 buildRootDept 口径：deptId=0、名称"飞书组织"、showOrder 0）
        result.depts.add(buildRootDeptRow());
        List<Department> departments =
                feishuDepartmentService.listAll(FeishuDepartmentService.ROOT_DEPT_ID);
        if (FuncUtil.isNotEmpty(departments)) {
            for (Department dept : departments) {
                result.depts.add(buildDeptRow(dept));
            }
        }
        List<String> deptIds = new ArrayList<>();
        deptIds.add(FeishuDepartmentService.ROOT_DEPT_ID);
        for (Department dept : departments) {
            if (FuncUtil.isNotEmpty(dept.getOpenDepartmentId())) {
                deptIds.add(dept.getOpenDepartmentId());
            }
        }
        List<User> users = feishuUserService.listByDepartments(deptIds);
        if (FuncUtil.isNotEmpty(users)) {
            for (User user : users) {
                result.users.add(buildUserRow(user));
            }
        }
        log.info("[飞书身份桥]组织同步拉数完成：部门 {} 行（含根），账号 {} 行",
                result.depts.size(), result.users.size());
        return result;
    }

    /**
     * 查询飞书 user_id（employee_id，管理员在飞书后台分配的英文工号/拼音）
     *
     * @return 账号身份键与登录名共用值；未开通字段权限或该用户未分配时返回 null，由调用方回退 open_id
     * （open_id 为 ou_+32 位十六进制不可读，无法作为对外登录账号，仅作内部兜底）
     */
    private String resolveUserName(String openId) {
        try {
            GetUserResp resp = feishuClient.contact().v3().user().get(GetUserReq.newBuilder()
                    .userId(openId)
                    .userIdType("open_id")
                    .build());
            if (resp.success() && resp.getData() != null && resp.getData().getUser() != null
                    && FuncUtil.isNotEmpty(resp.getData().getUser().getUserId())) {
                return resp.getData().getUser().getUserId();
            }
            log.warn("查询飞书 user_id 为空，登录名回退 open_id, openId: {}", openId);
        } catch (Exception e) {
            log.warn("查询飞书 user_id 失败，登录名回退 open_id, openId: {}", openId, e);
        }
        return null;
    }

    /**
     * 用授权 code 换取用户信息（原 getUserByCode 平移；失败抛运行时异常＝验票失败腿）
     */
    private FeishuSsoUser getUserByCode(String code) {
        CreateAccessTokenReq req = CreateAccessTokenReq.newBuilder()
                .createAccessTokenReqBody(CreateAccessTokenReqBody.newBuilder()
                        .grantType("authorization_code")
                        .code(code)
                        .build())
                .build();
        CreateAccessTokenResp resp;
        try {
            resp = feishuClient.authen().v1().accessToken().create(req);
        } catch (Exception e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "飞书授权失败: " + e.getMessage());
        }
        if (!resp.success() || resp.getData() == null) {
            log.error("飞书授权换取用户信息失败, code: {}, msg: {}", code, resp.getMsg());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "飞书授权失败: " + resp.getMsg());
        }
        FeishuSsoUser user = new FeishuSsoUser();
        user.setAccessToken(resp.getData().getAccessToken());
        user.setOpenId(resp.getData().getOpenId());
        user.setUnionId(resp.getData().getUnionId());
        user.setName(resp.getData().getName());
        user.setMobile(resp.getData().getMobile());
        user.setEmail(resp.getData().getEmail());
        user.setAvatarUrl(resp.getData().getAvatarUrl());
        return user;
    }

    /**
     * 桥侧身份主体：externalId/username 共用对齐键（user_id‖open_id，与同步链路同键）；
     * rawClaims 保留 IdP 返回原样值（标准键名 avatar/email/mobile 按 core SPI 约定，mobile
     * 不做 +86 归一——归一是入库口径，落库半边已退役）
     */
    private BridgeUserPrincipal buildPrincipal(FeishuSsoUser feishuUser, String identity) {
        BridgeUserPrincipal principal = new BridgeUserPrincipal();
        principal.externalId = identity;
        principal.username = identity;
        principal.displayName = feishuUser.getName();
        principal.avatar = feishuUser.getAvatarUrl();
        Map<String, Object> rawClaims = new LinkedHashMap<>();
        putIfNotBlank(rawClaims, "openId", feishuUser.getOpenId());
        putIfNotBlank(rawClaims, "unionId", feishuUser.getUnionId());
        putIfNotBlank(rawClaims, "email", feishuUser.getEmail());
        putIfNotBlank(rawClaims, "mobile", feishuUser.getMobile());
        putIfNotBlank(rawClaims, "avatar", feishuUser.getAvatarUrl());
        principal.rawClaims = rawClaims;
        return principal;
    }

    private OrgSyncResult.DeptRow buildRootDeptRow() {
        OrgSyncResult.DeptRow row = new OrgSyncResult.DeptRow();
        row.externalId = FeishuDepartmentService.ROOT_DEPT_ID;
        row.name = "飞书组织";
        row.showOrder = 0;
        return row;
    }

    private OrgSyncResult.DeptRow buildDeptRow(Department dept) {
        OrgSyncResult.DeptRow row = new OrgSyncResult.DeptRow();
        row.externalId = dept.getOpenDepartmentId();
        // 父引用沿用原 buildAcDept 口径：父为根（或空）挂 "0"，否则挂父 open_department_id；
        // 排序字段原链未消费（showOrder 仅根部门有值），SPI 无源列随收编为 null（登记于报告）
        if (FuncUtil.isNotEmpty(dept.getParentDepartmentId())
                && !FeishuDepartmentService.ROOT_DEPT_ID.equals(dept.getParentDepartmentId())) {
            row.parentExternalId = dept.getParentDepartmentId();
        } else {
            row.parentExternalId = FeishuDepartmentService.ROOT_DEPT_ID;
        }
        row.name = dept.getName();
        row.leader = dept.getLeaderUserId();
        return row;
    }

    private OrgSyncResult.UserRow buildUserRow(User user) {
        OrgSyncResult.UserRow row = new OrgSyncResult.UserRow();
        // 账号身份键与登录名统一使用飞书 user_id（employee_id）；未分配或权限不足时回退 open_id
        String userId = FuncUtil.isEmpty(user.getUserId()) ? user.getOpenId() : user.getUserId();
        row.externalId = userId;
        row.username = userId;
        // 权限不足时（仅基础只读）飞书不返回 name，回退用 openId（原链 NOT NULL 护栏同口径）
        row.displayName = FuncUtil.isEmpty(user.getName()) ? user.getOpenId() : user.getName();
        if (FuncUtil.isNotEmpty(user.getDepartmentIds())) {
            row.deptExternalId = user.getDepartmentIds()[0];
        }
        row.status = resolveStatus(user);
        row.email = user.getEmail();
        // +86 前缀归一随行（原链入库口径；内核落库按 row.mobile 原样入）
        row.mobile = FeishuMobileUtil.normalize(user.getMobile());
        row.avatar = user.getAvatar() == null ? null : user.getAvatar().getAvatarOrigin();
        return row;
    }

    /**
     * 在职状态二值映射（SPI 字典收敛，见类注释状态映射口径）：
     * 原链 active＝已激活且非离职非退出（单一布尔，无三态出口）→ active；
     * 其余（未激活/离职/退出）→ frozen（内核落库腿置停用，保住"离职不建号"漏斗行为）
     */
    private String resolveStatus(User user) {
        boolean active = user.getStatus() != null && Boolean.TRUE.equals(user.getStatus().getIsActivated())
                && !Boolean.TRUE.equals(user.getStatus().getIsResigned())
                && !Boolean.TRUE.equals(user.getStatus().getIsExited());
        return active ? STATUS_ACTIVE : STATUS_FROZEN;
    }

    private static void putIfNotBlank(Map<String, Object> claims, String key, String value) {
        if (FuncUtil.isNotEmpty(value)) {
            claims.put(key, value);
        }
    }
}
