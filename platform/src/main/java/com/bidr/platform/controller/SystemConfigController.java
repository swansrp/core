package com.bidr.platform.controller;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.cache.DictCacheService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: SystemConfigController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/27 22:07
 */
@Api(tags = {"系统基础接口"})
@RestController
@RequestMapping(path = {"/config"})
public class SystemConfigController {
    @Resource
    private SysConfigCacheService sysConfigCacheService;


    @RequestMapping(path = {""}, method = {RequestMethod.GET})
    public String getSysConfig(String configKey) {
        return sysConfigCacheService.getSysConfigValue(configKey);
    }
}
