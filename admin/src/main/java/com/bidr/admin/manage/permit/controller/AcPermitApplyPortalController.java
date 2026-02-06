package com.bidr.admin.manage.permit.controller;

import com.bidr.admin.manage.permit.service.AcPermitApplyPortalService;
import com.bidr.admin.manage.permit.vo.AcPermitApplyVO;
import com.bidr.authorization.dao.entity.AcPermitApply;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.permit.PermitApplyService;
import com.bidr.authorization.vo.permit.PermitApplyMenuTreeRes;
import com.bidr.authorization.vo.permit.PermitApplyVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限申请表管理控制器
 *
 * @author sharp
 * @since 2026-02-06
 */
@Api(tags = "系统管理 - 权限申请管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/permit/apply"})
@AdminPortal
public class AcPermitApplyPortalController extends BaseAdminController<AcPermitApply, AcPermitApplyVO> {

    private final AcPermitApplyPortalService acPermitApplyPortalService;
    private final PermitApplyService permitApplyService;

    @Override
    public PortalCommonService<AcPermitApply, AcPermitApplyVO> getPortalService() {
        return acPermitApplyPortalService;
    }

    @ApiOperation(value = "含申请数量的菜单树")
    @RequestMapping(value = "/menu/tree", method = RequestMethod.GET)
    public List<PermitApplyMenuTreeRes> getApplyMenuTree() {
        return permitApplyService.getMenuTree();
    }

    @ApiOperation(value = "申请页面权限", notes = "登录后准入")
    @RequestMapping(value = "", method = RequestMethod.POST)
    public void applyUserPermit(String url) {
        permitApplyService.applyUserPermit(AccountContext.getOperator(), url);
    }

    @ApiOperation(value = "获取页面权限现状", notes = "登录后准入")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public PermitApplyVO getUserPermitStatus(String url) {
        return permitApplyService.getUserPermit(AccountContext.getOperator(), url);
    }

    @ApiOperation(value = "同意权限申请")
    @RequestMapping(value = "/approve", method = RequestMethod.POST)
    public void approvePermit(Long id) {
        permitApplyService.approvePermit(id);
    }

    @ApiOperation(value = "拒绝权限申请")
    @RequestMapping(value = "/reject", method = RequestMethod.POST)
    public void rejectPermit(Long id) {
        permitApplyService.rejectPermit(id);
    }
}