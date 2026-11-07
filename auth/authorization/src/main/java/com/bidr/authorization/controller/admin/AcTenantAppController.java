package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.service.admin.AdminTenantAppService;
import com.bidr.authorization.vo.tenantapp.BindTenantAppReq;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppReq;
import com.bidr.authorization.vo.tenantapp.QueryTenantAppRes;
import com.bidr.authorization.vo.tenantapp.UnbindTenantAppReq;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;

/**
 * Title: AcTenantAppController
 * Description: 系统管理 - 租户应用开通 admin 面（方案B 棒1）。
 * <p>不继承 BaseAdminController：本表逻辑主键＝UK(tenant_id, app_code)，写面只有 bind/unbind
 * 两个语义端点（守卫＋幂等复活＋软删都在仓储腿），不提供通用 CRUD 面（M-3 许可不继承）。</p>
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Api(tags = "系统管理 - 租户应用开通")
@RestController("AcTenantAppController")
@RequestMapping(value = "/web/admin/tenant-app")
@Auth(AuthAdmin.class)
public class AcTenantAppController {

    @Resource
    private AdminTenantAppService adminTenantAppService;

    @ApiOperation(value = "获取租户开通应用列表")
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public Page<QueryTenantAppRes> query(@RequestBody @Validated QueryTenantAppReq req) {
        return adminTenantAppService.query(req);
    }

    @ApiOperation(value = "开通应用")
    @RequestMapping(value = "/bind", method = RequestMethod.POST)
    public void bind(@RequestBody @Validated BindTenantAppReq req) {
        adminTenantAppService.bind(req);
        Resp.notice("开通成功");
    }

    @ApiOperation(value = "取消开通")
    @RequestMapping(value = "/unbind", method = RequestMethod.POST)
    public void unbind(@RequestBody @Validated UnbindTenantAppReq req) {
        adminTenantAppService.unbind(req);
        Resp.notice("已取消开通");
    }
}
