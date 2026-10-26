package com.bidr.authorization.service.sso;

import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.utils.jwt.SsoJwtUtil;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * M23 introspect-ticket 判定逻辑（core 纯加法，决策稿 §七）。
 *
 * <p>验 SSO JWT 票 → 按 username 查 ac_user → 查 ac_user_tenant → 返回该用户全部租户
 * （id/code/name），可选按 app_code 查功能集（一期仅返回空集，等宿主侧实现）。</p>
 *
 * <p>🔴 不依赖 inkhub_* 业务表；功能集由宿主侧扩展（M23 后续批）。</p>
 *
 * @author sharp
 * @since 2026-10-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoIntrospectTicketService {

    private final AcUserService acUserService;
    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;

    /**
     * 验 SSO 票并返回用户身份 + 租户列表 + 应用功能集。
     *
     * @param idToken    SSO JWT 票（三段 base64url 点分串）
     * @param publicKey  验票公钥（X.509 base64）
     * @param appCode    目标应用编码（可选；一期仅回传，功能集由宿主侧扩展）
     * @param tenantCode 目标租户 code（可选；传了则只查该租户的组/功能）
     * @return 身份明细 + 租户列表 + 应用功能集
     */
    public SsoIntrospectTicketRes introspect(String idToken, String publicKey,
                                              String appCode, String tenantCode) {
        // 1. 验票
        String username = SsoJwtUtil.verifyAndGetUsername(idToken, publicKey);
        if (StringUtils.isBlank(username)) {
            return errorRes("SSO票验签失败或已过期");
        }

        // 2. 查用户
        AcUser acUser = acUserService.getUserByUserName(username);
        if (acUser == null) {
            return errorRes("用户不存在: " + username);
        }

        // 3. 查租户
        List<AcUserTenant> relations = acUserTenantService.listByUserId(acUser.getUserId());
        List<SsoIntrospectTicketRes.TenantItem> tenants = new ArrayList<>();
        if (relations != null && !relations.isEmpty()) {
            for (AcUserTenant rel : relations) {
                AcTenant tenant = acTenantService.getById(rel.getTenantId());
                if (tenant != null && "1".equals(tenant.getValid())) {
                    SsoIntrospectTicketRes.TenantItem item = new SsoIntrospectTicketRes.TenantItem();
                    item.setId(tenant.getId());
                    item.setCode(tenant.getCode());
                    item.setName(tenant.getName());
                    tenants.add(item);
                }
            }
        }

        // 4. 组装响应（功能集一期返回空集，等宿主侧 inkhub_app_group 实现后填充）
        SsoIntrospectTicketRes res = new SsoIntrospectTicketRes();
        res.setCustomerNumber(acUser.getCustomerNumber());
        res.setUserId(acUser.getUserId());
        res.setName(acUser.getName());
        res.setTenants(tenants);

        SsoIntrospectTicketRes.AppInfo app = new SsoIntrospectTicketRes.AppInfo();
        app.setAppCode(appCode);
        app.setTenantCode(tenantCode);
        app.setGroups(Collections.emptyList());
        app.setFunctions(Collections.emptyList());
        res.setApp(app);

        log.info("[introspect-ticket]username={}, tenants={}, app={}", username, tenants.size(), appCode);
        return res;
    }

    private SsoIntrospectTicketRes errorRes(String msg) {
        SsoIntrospectTicketRes res = new SsoIntrospectTicketRes();
        res.setError(msg);
        return res;
    }
}
