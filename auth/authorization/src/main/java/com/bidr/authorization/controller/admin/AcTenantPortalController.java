package com.bidr.authorization.controller.admin;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.service.tenant.AcTenantPortalService;
import com.bidr.authorization.vo.tenant.TenantUserLookupVO;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Title: AcTenantPortalController
 * Description: 租户管理面控制器（批乙棒4）。类级 {@code @Auth(AuthAdmin.class)} 压平台档端点
 * （目录/详情/建/改/停用，形态照 GroupBindController 的批甲档位先例）；
 * 本租户成员族端点以<b>方法级</b> {@code @Auth(AuthLogin.class)} 反向豁免登录档进入，
 * 判权唯一收口在 {@link AcTenantPortalService#guardTenantAdmin} ——平台档或现查
 * ac_user_tenant 的 is_admin，非本租户且非平台一律拒绝。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Api(tags = "系统管理 - 租户管理")
@RestController
@RequestMapping(value = "/web/tenant/portal")
@Auth(AuthAdmin.class)
@RequiredArgsConstructor
public class AcTenantPortalController {

    private final AcTenantPortalService acTenantPortalService;

    // ==================== 平台档面（类级 AuthAdmin） ====================

    @ApiOperation(value = "租户目录列表（默认仅有效租户；includeDisabled=true 含停用行——管理面回看/启用入口）")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<AcTenant> list(@RequestParam(value = "includeDisabled", required = false, defaultValue = "false") boolean includeDisabled) {
        return acTenantPortalService.listTenants(includeDisabled);
    }

    @ApiOperation(value = "租户详情")
    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    public AcTenant detail(@RequestParam Long id) {
        return acTenantPortalService.getTenantDetail(id);
    }

    @ApiOperation(value = "新建租户")
    @RequestMapping(value = "/create", method = RequestMethod.POST)
    public void create(@RequestParam String name, @RequestParam(required = false) String code) {
        acTenantPortalService.createTenant(name, code);
        Resp.notice("租户创建成功");
    }

    @ApiOperation(value = "修改租户")
    @RequestMapping(value = "/update", method = RequestMethod.POST)
    public void update(@RequestParam Long id, @RequestParam String name,
                       @RequestParam(required = false) String code) {
        acTenantPortalService.updateTenant(id, name, code);
        Resp.notice("租户修改成功");
    }

    @ApiOperation(value = "停用租户(软删)")
    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public void disable(@RequestParam Long id) {
        acTenantPortalService.disableTenant(id);
        Resp.notice("租户已停用");
    }

    @ApiOperation(value = "启用租户（恢复停用行 valid='1'，对称 disable）")
    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public void enable(@RequestParam Long id) {
        acTenantPortalService.enableTenant(id);
        Resp.notice("租户已启用");
    }

    /**
     * 加成员前按人精确查（工号/手机号/登录名/姓名 四路等值）：平台档面形态（类级 AuthAdmin
     * 挂闸，同 /list /detail——判权不在 service 再收口）。🔴 不做 /my 反向豁免的硬理由：
     * AuthAdminGateConventionTest 针1/针2 把反向豁免端点数与 guardTenantAdmin 调用点钉死＝4
     * （防漂移针，动它须显式改针），且 keyword-only 契约无 tenantId 供 guard 收口；平台档本就是
     * /my/member/add 判权矩阵的放行超集，不放大。返回形态照 {@link #myMembers}
     * （数据直出进 Resp payload，不加 notice）。
     */
    @ApiOperation(value = "加成员前按工号/手机号/姓名精确查人")
    @RequestMapping(value = "/my/lookup-user", method = RequestMethod.GET)
    public TenantUserLookupVO lookupMyUser(@RequestParam String keyword) {
        return acTenantPortalService.lookupUser(keyword);
    }

    // ==================== 本租户面（方法级 AuthLogin 反向豁免，service 现查判权） ====================

    @ApiOperation(value = "本租户成员清单")
    @Auth(AuthLogin.class)
    @RequestMapping(value = "/my/members", method = RequestMethod.GET)
    public List<Map<String, Object>> myMembers(@RequestParam Long tenantId) {
        return acTenantPortalService.listMembers(tenantId);
    }

    @ApiOperation(value = "本租户加成员")
    @Auth(AuthLogin.class)
    @RequestMapping(value = "/my/member/add", method = RequestMethod.POST)
    public void addMyMember(@RequestParam Long tenantId, @RequestParam Long userId,
                            @RequestParam(required = false, defaultValue = "false") Boolean isAdmin) {
        acTenantPortalService.addMember(tenantId, userId, isAdmin);
        Resp.notice("成员已加入");
    }

    @ApiOperation(value = "本租户移成员(物理删关系行)")
    @Auth(AuthLogin.class)
    @RequestMapping(value = "/my/member/remove", method = RequestMethod.POST)
    public void removeMyMember(@RequestParam Long tenantId, @RequestParam Long userId) {
        acTenantPortalService.removeMember(tenantId, userId);
        Resp.notice("成员已移除");
    }

    @ApiOperation(value = "本租户切换成员 is_admin")
    @Auth(AuthLogin.class)
    @RequestMapping(value = "/my/member/admin", method = RequestMethod.POST)
    public void setMyMemberAdmin(@RequestParam Long tenantId, @RequestParam Long userId,
                                 @RequestParam Boolean isAdmin) {
        acTenantPortalService.setMemberAdmin(tenantId, userId, isAdmin);
        Resp.notice("管理员标识已更新");
    }
}
