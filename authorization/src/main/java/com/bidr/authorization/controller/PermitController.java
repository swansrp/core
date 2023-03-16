package com.bidr.authorization.controller;

import com.bidr.authorization.service.permit.PermitService;
import com.bidr.authorization.vo.permit.PermitTreeItem;
import com.bidr.authorization.vo.permit.PermitTreeRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: PermitController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:04
 */
@Api(value = "权限操作", tags = "权限操作")
@RestController("PermitController")
@RequestMapping(value = "")
public class PermitController {
    @Resource
    private PermitService permitService;

    @ApiOperation(value = "权限树", notes = "登录后准入")
    @RequestMapping(value = "/permitTree", method = RequestMethod.GET)
    public List<PermitTreeRes> getPermitTree() {
        return permitService.getPermitTree();
    }

    @ApiOperation(value = "权限列表", notes = "登录后准入")
    @RequestMapping(value = "/permitList", method = RequestMethod.GET)
    public List<PermitTreeItem> getPermitList() {
        return permitService.getPermitList();
    }

}
