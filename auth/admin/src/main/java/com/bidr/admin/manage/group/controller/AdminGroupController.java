package com.bidr.admin.manage.group.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.kernel.controller.BaseAdminTreeController;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: GroupAdminController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/16 09:56
 */
@Api(tags = "系统管理 - 用户组-用户 - 绑定管理")
@RequiredArgsConstructor
@RestController("AdminGroupController")
@RequestMapping(value = "/web/admin/group")
public class AdminGroupController extends BaseAdminTreeController<AcGroup, AcGroup> {

    @Override
    protected SFunction<AcGroup, ?> id() {
        return AcGroup::getId;
    }

    @Override
    protected SFunction<AcGroup, Integer> order() {
        return AcGroup::getDisplayOrder;
    }

    @Override
    protected SFunction<AcGroup, ?> pid() {
        return AcGroup::getPid;
    }

    @Override
    protected SFunction<AcGroup, String> name() {
        return AcGroup::getName;
    }
}
