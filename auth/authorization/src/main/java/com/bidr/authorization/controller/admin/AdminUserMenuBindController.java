package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserMenu;
import com.bidr.authorization.service.permit.MenuService;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.authorization.vo.user.UserRes;
import com.bidr.kernel.controller.BaseBindController;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户菜单绑定控制器(权限直授)
 *
 * @author sharp
 */
@Api(tags = "系统管理 - 用户 - 权限 - 绑定管理")
@RestController("AdminUserMenuBindController")
@RequestMapping(value = "/web/admin/user/menu")
@RequiredArgsConstructor
public class AdminUserMenuBindController extends BaseBindController<AcUser, AcUserMenu, AcMenu, UserRes, AcMenu> {

    @ApiOperation(value = "获取用户对应权限树", notes = "全部；appCode 缺省/空＝平台自身（方案B 棒3）")
    @RequestMapping(value = "/menu/tree", method = RequestMethod.GET)
    public List<MenuTreeRes> getMenuTree(String entityId, @RequestParam(required = false) String appCode) {
        // this. 而非 super.：虚派发到同一实现（本类无覆写，生产行为不变），留出测试拦截缝
        List<AcMenu> bindList = MenuService.narrowByAppCode(getBindList(entityId), appCode);
        for (AcMenu acMenu : bindList) {
            if (FuncUtil.isEmpty(acMenu.getPid())) {
                acMenu.setPid(acMenu.getGrandId());
            }
        }
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, bindList, AcMenu::getMenuId,
                AcMenu::getPid);
    }

    @Override
    protected SFunction<AcUserMenu, ?> bindAttachId() {
        return AcUserMenu::getMenuId;
    }

    @Override
    protected SFunction<AcMenu, ?> attachId() {
        return AcMenu::getMenuId;
    }

    @Override
    protected SFunction<AcUserMenu, ?> bindEntityId() {
        return AcUserMenu::getCustomerNumber;
    }

    @Override
    protected SFunction<AcUser, ?> entityId() {
        return AcUser::getUserId;
    }
}
