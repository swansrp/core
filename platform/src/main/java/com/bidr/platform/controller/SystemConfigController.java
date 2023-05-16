package com.bidr.platform.controller;

import com.bidr.platform.service.cache.SysConfigCacheService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: SystemConfigController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/27 22:07
 */
@Api(tags = "系统基础 - 参数接口")
@RestController
@RequestMapping(path = {"/web/config"})
public class SystemConfigController {
    @Resource
    private SysConfigCacheService sysConfigCacheService;


    @RequestMapping(path = {""}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取系统参数")
    public String getSysConfig(String configKey) {
        return sysConfigCacheService.getSysConfigValue(configKey);
    }
}
