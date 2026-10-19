package com.bidr.admin.manage.dept.controller;


import com.bidr.admin.controller.common.BaseExcelParseController;
import com.bidr.admin.manage.dept.service.AdminDeptExcelService;
import com.bidr.admin.manage.dept.vo.DeptUserVO;
import com.bidr.admin.service.common.BaseExcelParseService;
import com.bidr.authorization.dao.entity.AcUserDept;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Sharp
 * @since 2026/3/20 10:00
 */
@Api(tags = "系统管理 - 部门 - 部门用户导入")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/dept/user/excel")
public class AdminDeptExcelController extends BaseExcelParseController<AcUserDept, DeptUserVO> {

    private final AdminDeptExcelService adminDeptExcelService;

    @Override
    protected BaseExcelParseService<AcUserDept, DeptUserVO> getExcelParseService() {
        return adminDeptExcelService;
    }
}
