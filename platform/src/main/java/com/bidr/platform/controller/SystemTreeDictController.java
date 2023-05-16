package com.bidr.platform.controller;

import com.bidr.platform.bo.tree.TreeDict;
import com.bidr.platform.service.cache.DictTreeCacheService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: SystemTreeDictController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/04 14:08
 */
@Api(tags = "系统基础 - 参数接口")
@RestController
@RequestMapping(path = {"/web/tree"})
public class SystemTreeDictController {
    @Resource
    private DictTreeCacheService dictTreeCacheService;


    @RequestMapping(path = {""}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取树形字典")
    public List<TreeDict> getTree(String dictName) {
        return dictTreeCacheService.getCache(dictName);
    }
}
