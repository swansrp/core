package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.vo.admin.DepartmentRes;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AdminDeptController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/20 11:48
 */
@Api(tags = "系统管理 - 部门管理")
@AdminPortal
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/portal/department"})
public class AdminDeptController extends BaseAdminTreeController<AcDept, DepartmentRes> {


    @Override
    protected SFunction<AcDept, ?> id() {
        return AcDept::getDeptId;
    }

    @Override
    protected SFunction<AcDept, Integer> order() {
        return AcDept::getShowOrder;
    }

    @Override
    protected SFunction<AcDept, ?> pid() {
        return AcDept::getPid;
    }

    @Override
    protected SFunction<AcDept, String> name() {
        return AcDept::getName;
    }


}
