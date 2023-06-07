package com.bidr.platform.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.service.params.ParamService;
import com.bidr.platform.vo.params.QuerySysConfigReq;
import com.bidr.platform.vo.params.SysConfigRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminConfigController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/07 14:00
 */
@Api(tags = "系统管理 - 参数管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/config/admin"})
public class AdminConfigController extends BaseAdminController<SysConfig, SysConfigRes> {

    @Resource
    private ParamService paramService;

    @ApiOperation("查询参数列表")
    @RequestMapping(path = {"/query"}, method = {RequestMethod.POST})
    public Page<SysConfigRes> query(@RequestBody QuerySysConfigReq req) {
        return paramService.query(req);
    }
}
