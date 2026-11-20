package com.bidr.admin.controller.common;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.kernel.utils.ConditionVariableUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 条件动态变量清单控制器。
 * <p>前端筛选面板 / PortalConfig 默认值编辑器的「插入变量」下拉启动时拉取本清单渲染选项，
 * 后端 {@link ConditionVariableUtil} 是唯一正源，新增 token 前端零改动自动可见。</p>
 * <p>仅需登录（管理员配置与运行态用户选变量都要用），故类级 {@code @Auth(AuthLogin.class)}，不走 admin 门禁。</p>
 *
 * @author Sharp
 */
@Api(tags = "系统基础 - 条件动态变量清单")
@RestController
@RequestMapping(value = "/web/admin/portal/condition-variable")
@Auth(AuthLogin.class)
public class ConditionVariablePortalController {

    @ApiOperation("变量清单（下拉选项唯一正源）")
    @GetMapping(value = "/catalog")
    public List<Map<String, Object>> catalog() {
        return ConditionVariableUtil.catalog();
    }
}
