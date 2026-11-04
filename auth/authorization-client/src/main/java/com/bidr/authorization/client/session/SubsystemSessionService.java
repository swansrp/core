package com.bidr.authorization.client.session;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.client.AuthzCheckApi;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.identity.mirror.dao.repository.IdmTenantMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserTenantMirrorService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: SubsystemSessionService
 * Description: 子系统自签会话的共享编排——所有接墨枢 SSO 的子系统同一条链：
 * <b>身份镜像闸 → 租户归属闸 → 零租户回 provider 判平台管理员 → 双票签发</b>。
 *
 * <p>身份源固定为 {@code identity-mirror}（镜像为子系统唯一账号源，G9：镜像只承载「这是谁」，
 * 零角色字段）；会话票据由 {@link TokenService}（client 运行时）就地签发，不依赖
 * {@code authorization}(server) 的 LoginService。登录名从哪来由调用方决定
 * （epc＝idm 拦截器装配的 {@code IdmHolder}，其它子系统可能是表单/工号），本类只收字符串。</p>
 *
 * <p>🔴 零租户豁免只认 provider：平台管理员设计上不归属任何租户、可切换各租户，
 * 故空租户集时回 {@code /authz/check}（{@code scope=""}＋{@code permission="admin"}，
 * 与 core {@code RemoteAuthzChecker} 同约定）实时判，判是才放行；判据不回本地猜 uid、
 * 不向镜像加角色字段。provider 未装配/不可达／信封非成功一律 fail-closed（只挡零租户用户，
 * 正常有租户的用户不发起本判定）。</p>
 *
 * <p>🔴 装配条件：本类只在宿主带 {@code identity-mirror} 时由
 * {@link SubsystemSessionAutoConfiguration} 注册。主系统（墨枢）经
 * {@code authorization → authorization-client} 拿到本 jar，但无镜像（optional 依赖不传递），
 * 自动退位——不建 idm_* 表、不注册本 bean。</p>
 *
 * @author Sharp
 * @since 2026-11-04
 */
@Slf4j
@RequiredArgsConstructor
public class SubsystemSessionService {

    /** 与 core RemoteAuthzChecker 同约定：admin 判定＝scope 空串＋权限码 admin */
    static final String ADMIN_SCOPE = "";
    static final String ADMIN_PERMISSION = "admin";

    /** 身份镜像闸（idm_user_mirror 查人，镜像为唯一账号源） */
    private final IdmUserMirrorService userMirrorService;
    /** 租户归属（idm_user_tenant_mirror 现查本用户关系行） */
    private final IdmUserTenantMirrorService userTenantMirrorService;
    /** 租户目录（idm_tenant_mirror 现查有效租户） */
    private final IdmTenantMirrorService tenantMirrorService;
    /** core client 会话签发（TokenService 实现由宿主提供，子系统侧为 TokenServiceImpl） */
    private final TokenService tokenService;
    /** 平台管理员回判（/authz/check，零租户会话的唯一豁免依据） */
    private final ObjectProvider<AuthzCheckApi> authzCheckApi;

    /**
     * 登录：镜像闸 → 租户闸 → 签发本地会话。
     *
     * <p>🔴 任一闸不过即拒（{@link ServiceException}）：登录名缺失；镜像无此人；镜像已停用；
     * 非管理员零有效租户归属（空租户集在 core 是读旁路，绝不放行）。</p>
     *
     * @param userName 登录名（子系统侧身份源，如 IdmHolder.username）
     * @return 本地会话（accessToken/refreshToken/name/customerNumber）
     */
    public LoginRes login(String userName) {
        if (userName == null || userName.trim().isEmpty()) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "登录身份缺失，请重新登录");
        }
        IdmUserMirror mirror = userMirrorService.findByUserName(userName);
        if (mirror == null) {
            log.warn("[登录闸]镜像查无此人，拒绝: username={}", userName);
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                    "该账号尚未同步到本系统，请从墨枢重新登录后再试，或联系管理员开通");
        }
        if (!CommonConst.YES.equals(mirror.getValid())) {
            log.warn("[登录闸]镜像账号已停用，拒绝: username={} mirrorId={}", userName, mirror.getId());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "该账号已停用，请联系管理员");
        }

        List<Long> tenantIds = validTenantIds(mirror.getId());
        if (tenantIds.isEmpty() && !isPlatformAdmin(mirror.getId())) {
            log.warn("[登录闸]无有效租户归属且非系统管理员，拒绝: username={} mirrorId={}",
                    userName, mirror.getId());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                    "当前用户无有效租户归属，请联系主系统管理员分配租户");
        }
        return issueSession(mirror, tenantIds);
    }

    /**
     * 刷新登录态（旧 refreshToken 一次性：校验通过即销毁，再签一对新票）。
     *
     * <p>🔴 必须钉票类型：accessToken 与 refreshToken 同在一个 Redis 命名空间、decode 不校验类型，
     * 不判类型就等于任何有效 access 都能无限续期，且绕过「refresh 一次性」。</p>
     *
     * <p>🔴 与 {@link #login(String)} 同一租户口径：空租户集仅对平台管理员放行
     * （回 {@code /authz/check} 实时判）；镜像 id 恒等于上游 uid，refresh 侧无需另外的身份源。</p>
     *
     * @param refreshToken 登录时下发的 refreshToken（三段票原串）
     * @return 新本地会话（accessToken/refreshToken/name/customerNumber）
     */
    public LoginRes refresh(String refreshToken) {
        TokenInfo token = decodeQuietly(refreshToken);
        if (token == null || token.getType() != TokenType.WEB_REFRESH_TOKEN) {
            log.warn("[刷新闸]refreshToken 无效或类型不符，拒绝");
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "登录已过期，请重新登录");
        }
        if (!tokenService.isTokenExist(token)) {
            log.warn("[刷新闸]refreshToken 已过期，拒绝");
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "登录已过期，请重新登录");
        }
        String customerNumber = token.getCustomerNumber();
        IdmUserMirror mirror = userMirrorService.findByCustomerNumber(customerNumber);
        if (mirror == null || !CommonConst.YES.equals(mirror.getValid())) {
            log.warn("[刷新闸]镜像无此人或已停用，拒绝: customerNumber={}", customerNumber);
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "账号状态异常，请重新登录");
        }
        // 旧 refresh 一次性（与 server LoginServiceImpl#refreshLogin 同款：验过即销毁）
        tokenService.removeToken(token);

        List<Long> tenantIds = validTenantIds(mirror.getId());
        if (tenantIds.isEmpty() && !isPlatformAdmin(mirror.getId())) {
            log.warn("[刷新闸]无有效租户归属且非系统管理员，拒绝: customerNumber={} mirrorId={}",
                    customerNumber, mirror.getId());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                    "当前用户无有效租户归属，请联系主系统管理员分配租户");
        }
        return issueSession(mirror, tenantIds);
    }

    /**
     * 判定指定上游用户（ac_user.user_id，与镜像 id 同值）是否平台管理员。
     *
     * @param userId 上游用户 id（镜像 id）
     * @return true=平台管理员；null/未装配/调用失败一律 false（fail-closed）
     */
    public boolean isPlatformAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        AuthzCheckApi api = authzCheckApi.getIfAvailable();
        if (api == null) {
            log.error("[管理员判定]/authz 客户端未装配（my.authz.client.enabled 未开），零租户一律拒绝");
            return false;
        }
        try {
            return api.check(userId, ADMIN_SCOPE, ADMIN_PERMISSION);
        } catch (RuntimeException e) {
            log.warn("[管理员判定]/authz/check 不可用，fail-closed 按非管理员处理: userId={} err={}",
                    userId, e.getMessage());
            return false;
        }
    }

    /**
     * 签发本地会话：core client {@link TokenService} 造双票 → 烤入身份/租户项 → 出串收尾。
     *
     * <p>🔴 不写 {@code ROLE_MAP}／{@code PERMIT_LIST}：子系统镜像无角色字段（G9），client
     * {@code AuthLogin.buildContext} 对缺键容忍（{@code JsonUtil.readJson(null,…)} ⇒ null、
     * {@code containsAdminRole(null,…)} ⇒ false，M-43 已证）。</p>
     *
     * @param mirror    镜像用户行（id＝上游 user_id、customerNumber＝工号）
     * @param tenantIds 有效租户 id 集（非管理员登录链已保证非空；平台管理员可为空集）
     * @return 本地会话
     */
    private LoginRes issueSession(IdmUserMirror mirror, List<Long> tenantIds) {
        String customerNumber = mirror.getCustomerNumber();
        TokenInfo access = tokenService.buildWebAccessToken(customerNumber);
        TokenInfo refreshToken = tokenService.buildWebRefreshToken(customerNumber);
        tokenService.putItem(access, TokenItem.USER_ID.name(), mirror.getId());
        tokenService.putItem(access, TokenItem.OPERATOR.name(), customerNumber);
        if (mirror.getName() != null) {
            tokenService.putItem(access, TokenItem.NICK_NAME.name(), mirror.getName());
        }
        if (mirror.getUserName() != null) {
            tokenService.putItem(access, TokenItem.USER_NAME.name(), mirror.getUserName());
        }
        if (tenantIds != null && !tenantIds.isEmpty()) {
            tokenService.putItem(access, TokenItem.TENANT_IDS.name(), tenantIds);
            tokenService.putItem(access, TokenItem.TENANT_ID.name(), tenantIds.get(0));
        }

        LoginRes res = new LoginRes();
        res.setUserId(mirror.getId());
        res.setCustomerNumber(customerNumber);
        res.setName(mirror.getName());
        res.setAccessToken(AuthTokenUtil.getToken(access));
        res.setRefreshToken(AuthTokenUtil.getToken(refreshToken));
        // 与 server fillToken 同款收尾：同请求内 tokenService.getTokenValue() 需要线程态
        TokenHolder.set(access);
        return res;
    }

    /**
     * 本用户有效租户 id 集：镜像关系行（现查）× 有效租户目录，两侧 {@code valid='1'} 才收；
     * 按关系行顺序去重保序。
     *
     * @param mirrorUserId 镜像用户 id（null ⇒ 空集）
     * @return 有效租户 id 列表（可能为空）
     */
    private List<Long> validTenantIds(Long mirrorUserId) {
        if (mirrorUserId == null) {
            return Collections.emptyList();
        }
        Set<Long> validTenantIds = new LinkedHashSet<>();
        for (IdmTenantMirror tenant : nullToEmpty(tenantMirrorService.listAll())) {
            if (tenant != null && tenant.getId() != null && CommonConst.YES.equals(tenant.getValid())) {
                validTenantIds.add(tenant.getId());
            }
        }
        if (validTenantIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (IdmUserTenantMirror relation : nullToEmpty(userTenantMirrorService.listByUserId(mirrorUserId))) {
            if (relation == null || relation.getTenantId() == null
                    || !CommonConst.YES.equals(relation.getValid())) {
                continue;
            }
            if (validTenantIds.contains(relation.getTenantId())) {
                result.add(relation.getTenantId());
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 解析 refreshToken 三段票；畸形票一律按「无效」处理（不把解析异常漏成 500）。
     */
    private static TokenInfo decodeQuietly(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return null;
        }
        try {
            return AuthTokenUtil.decode(refreshToken);
        } catch (Exception e) {
            log.warn("[刷新闸]refreshToken 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
