package com.bidr.authorization.controller;

import com.bidr.authorization.service.department.DepartmentService;
import com.bidr.authorization.vo.department.DepartmentTreeRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: DepartmentController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/21 18:24
 */
@Api(value = "人事信息", tags = "人事信息")
@RestController("DepartmentController")
@RequestMapping(value = "/web/account/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @ApiOperation(value = "获取组织结构树", notes = "登录后准入")
    @RequestMapping(value = "/tree", method = RequestMethod.GET)
    public List<DepartmentTreeRes> getDepartmentTree() {
        return departmentService.getDeptTree();
    }


}
