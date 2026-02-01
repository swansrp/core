package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcGroupMenu;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户组菜单绑定控制器
 *
 * @author sharp
 */
@Api(tags = "系统管理 - 用户组 - 权限 - 绑定管理")
@RestController("AdminGroupMenuBindController")
@RequestMapping(value = "/web/admin/group/menu")
@RequiredArgsConstructor
public class AdminGroupMenuBindController extends BaseBindController<AcGroup, AcGroupMenu, AcMenu, AcGroup, AcMenu> {

    @ApiOperation(value = "获取用户组对应菜单树", notes = "全部")
    @RequestMapping(value = "/menu/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree(String entityId) {
        List<AcMenu> bindList = super.getBindList(entityId);
        for (AcMenu acMenu : bindList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, bindList, AcMenu::getMenuId,
                AcMenu::getPid);
    }

    @Override
    protected SFunction<AcGroupMenu, ?> bindAttachId() {
        return AcGroupMenu::getMenuId;
    }

    @Override
    protected SFunction<AcMenu, ?> attachId() {
        return AcMenu::getMenuId;
    }

    @Override
    protected SFunction<AcGroupMenu, ?> bindEntityId() {
        return AcGroupMenu::getGroupId;
    }

    @Override
    protected SFunction<AcGroup, ?> entityId() {
        return AcGroup::getId;
    }
}
