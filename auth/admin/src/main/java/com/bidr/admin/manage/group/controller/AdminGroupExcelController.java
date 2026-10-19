package com.bidr.admin.manage.group.controller;


import com.bidr.admin.controller.common.BaseExcelParseController;
import com.bidr.admin.manage.group.service.AdminGroupExcelService;
import com.bidr.admin.manage.group.vo.GroupUserVO;
import com.bidr.admin.service.common.BaseExcelParseService;
import com.bidr.authorization.dao.entity.AcUserGroup;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Sharp
 * @since 2026/3/20 09:25
 */
@Api(tags = "系统管理 - 用户组 - 用户组成员导入")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/group/user/excel")
public class AdminGroupExcelController extends BaseExcelParseController<AcUserGroup, GroupUserVO> {

    private final AdminGroupExcelService adminGroupExcelService;

    @Override
    protected BaseExcelParseService<AcUserGroup, GroupUserVO> getExcelParseService() {
        return adminGroupExcelService;
    }
}
