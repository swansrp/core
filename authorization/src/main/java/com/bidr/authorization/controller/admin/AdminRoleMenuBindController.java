package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.service.admin.AdminRoleMenuBindService;
import com.bidr.authorization.service.admin.AdminRoleService;
import com.bidr.authorization.vo.admin.QueryRoleReq;
import com.bidr.authorization.vo.admin.RoleReq;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.bind.QueryBindReq;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AdminRoleMenuBindController
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 13:36
 */
@Api(tags = "系统角色管理")
@RestController("AdminRoleMenuBindController")
@RequestMapping(value = "/web/role/admin")
@RequiredArgsConstructor
public class AdminRoleMenuBindController extends BaseBindController<AcMenu, AcRoleMenu, AcRole, AcMenu, RoleRes> {

    private final AdminRoleMenuBindService adminRoleMenuBindService;
    private final AdminRoleService adminRoleService;

    @ApiOperation(value = "获取角色列表", notes = "全部")
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public Page<RoleRes> queryRole(@RequestBody QueryRoleReq req) {
        return adminRoleService.queryRole(req);
    }

    @ApiOperation(value = "创建角色", notes = "全部")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public void addRole(@RequestBody RoleReq req) {
        adminRoleService.addRole(req);
        Resp.notice("创建角色成功");
    }

    @Override
    @ApiOperation("删除数据")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    public void delete(@RequestBody IdReqVO vo) {
        adminRoleService.deleteRole(vo.getId());
        Resp.notice("删除成功");
    }

    @ApiOperation(value = "获取角色对应菜单树", notes = "全部")
    @RequestMapping(value = "/menu/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree(Long slaveId) {
        QueryBindReq req = new QueryBindReq();
        req.setSalveId(slaveId);
        IPage<AcMenu> bindList = adminRoleMenuBindService.getBindList(req);
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, bindList.getRecords(), AcMenu::getMenuId,
                AcMenu::getPid);
    }

    @Override
    protected BaseBindRepo<AcMenu, AcRoleMenu, AcRole> bindRepo() {
        return adminRoleMenuBindService;
    }
}
