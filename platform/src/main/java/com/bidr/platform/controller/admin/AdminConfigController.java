package com.bidr.platform.controller.admin;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.service.dict.DictService;
import com.bidr.platform.service.params.ParamService;
import com.bidr.platform.vo.params.SysConfigRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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
@RequestMapping(path = {"/web/config/admin", "/web/portal/sysConfig"})
public class AdminConfigController extends BaseAdminController<SysConfig, SysConfigRes> {

    @Resource
    private ParamService paramService;
    @Resource
    private DictService dictService;

    @ApiOperation("刷新缓存")
    @RequestMapping(path = {"/refresh"}, method = {RequestMethod.POST})
    public void refresh() {
        paramService.refresh();
        dictService.refresh();
        Resp.notice("系统参数修改已生效");
    }


}
