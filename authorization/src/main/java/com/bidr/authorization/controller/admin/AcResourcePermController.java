package com.bidr.authorization.controller.admin;

import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.authorization.dao.repository.AcResourcePermService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.ResourcePermFilterService;
import com.bidr.authorization.vo.perm.ResourcePermSaveBySubjectReq;
import com.bidr.authorization.vo.perm.ResourcePermSaveReq;
import com.bidr.kernel.config.response.Resp;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通用资源权限管理Controller
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Api(tags = "系统管理 - 通用资源权限")
@RestController
@RequestMapping(value = "/web/resource-perm")
@RequiredArgsConstructor
public class AcResourcePermController {

    private final AcResourcePermService acResourcePermService;
    private final ResourcePermFilterService resourcePermFilterService;

    @ApiOperation(value = "查询某资源的授权列表")
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<AcResourcePerm> list(@RequestParam String resourceType, @RequestParam String resourceId) {
        return acResourcePermService.getByResource(resourceType, resourceId);
    }

    @ApiOperation(value = "批量保存某资源的授权配置（全量覆盖）")
    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public void save(@Validated @RequestBody ResourcePermSaveReq req) {
        acResourcePermService.savePerms(req, AccountContext.getOperator());
        Resp.notice("保存成功");
    }

    @ApiOperation(value = "清空某资源的授权（恢复为不限制）")
    @RequestMapping(value = "/clear", method = RequestMethod.DELETE)
    public void clear(@RequestParam String resourceType, @RequestParam String resourceId) {
        acResourcePermService.deleteByResource(resourceType, resourceId);
        Resp.notice("已清除权限配置");
    }

    @ApiOperation(value = "检查当前用户是否有某资源的权限")
    @RequestMapping(value = "/check", method = RequestMethod.GET)
    public boolean check(@RequestParam String resourceType, @RequestParam String resourceId) {
        return resourcePermFilterService.hasPermission(resourceType, resourceId);
    }

    @ApiOperation(value = "查询某主体已授权的资源ID列表（反向查询）")
    @RequestMapping(value = "/list-by-subject", method = RequestMethod.GET)
    public List<String> listBySubject(@RequestParam String resourceType,
                                      @RequestParam Integer subjectType,
                                      @RequestParam String subjectId) {
        return acResourcePermService.listResourceIdsBySubject(resourceType, subjectType, subjectId);
    }

    @ApiOperation(value = "按主体全量设置授权资源（反向配置，diff增删）")
    @RequestMapping(value = "/save-by-subject", method = RequestMethod.POST)
    public void saveBySubject(@Validated @RequestBody ResourcePermSaveBySubjectReq req) {
        acResourcePermService.saveBySubject(req, AccountContext.getOperator());
        Resp.notice("保存成功");
    }
}
