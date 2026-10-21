package com.bidr.authorization.controller.login;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.service.tenant.TenantMyService;
import com.bidr.authorization.vo.login.TenantMyVO;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: TenantMyController
 * Description: 框架内置「我的租户」清单与切换口（2026-10-21 用户拍板：多租户系统内置齿轮切换面，
 * 系统管理员可切所有租户）。独立 bean 名 + 独立路径 {@code /web/tenant/my}：不与
 * {@code AcTenantPortalController}（平台档租户目录/成员管理，类级 AuthAdmin）混装——
 * 本口是登录档 UX 面，提权与否由 {@code TenantMyService} 按账号判定，不走切面档位
 * （🔴 {@code TenantContext.isPlatform()} 在 AuthLogin 档下恒 false，10-20 撤回的正是这条死路）。
 * <p>
 * 切换＝改写当前 token 的 TENANT_ID 键（提权档连 TENANT_IDS 一并改写，数据闸跟切），
 * 免重登录；下一请求经 AuthLogin 重建线程态。成员关系闸对普通成员不变。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
@Api(tags = "系统基础 - 我的租户")
@RestController("TenantMyController")
@RequestMapping(value = "/web/tenant/my")
@Auth(AuthLogin.class)
public class TenantMyController {

    @Resource
    private TenantMyService tenantMyService;

    /**
     * 我的租户清单（现查；平台档/系统管理级＝全部有效租户，普通成员＝成员关系 ∩ 有效租户）。
     */
    @ApiOperation(value = "我的租户清单",
            notes = "现查不消费登录烤键（M-11）：平台档（sys_config ACCOUNT_ADMIN_ROLE_ID × 角色）"
                    + "或系统管理级（登录烤的租户集为空）＝全部有效租户；普通成员＝成员关系 ∩ 有效租户"
                    + "（停用租户不展示）。isAdmin＝关系行 is_admin 现查；current＝token TENANT_ID 命中。")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<TenantMyVO> myList() {
        return tenantMyService.listMy();
    }

    /**
     * 切换当前租户（改写 token，免重登录）。
     *
     * @param tenantId 目标租户 id；平台档/系统管理级＝任意有效租户，普通成员须为本租户成员
     */
    @ApiOperation(value = "切换当前租户",
            notes = "改写当前 token 的 TENANT_ID（提权档连 TENANT_IDS 一并改写，数据闸跟切），免重登录。"
                    + "平台档/系统管理级＝任意有效租户；普通成员＝成员关系现查必命中且租户有效，"
                    + "停用/非成员 ⇒ 业务拒绝（文案即根因）。")
    @RequestMapping(value = "/switch", method = RequestMethod.POST)
    public void mySwitch(@RequestParam("tenantId") Long tenantId) {
        tenantMyService.switchTenant(tenantId);
        Resp.notice("租户已切换");
    }
}
