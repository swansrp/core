package com.bidr.platform.controller;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.service.dict.DictService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: SystemDictController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/27 17:06
 */
@Api(tags = "系统基础 - 参数接口")
@RestController
@RequestMapping(path = {"/web/dict"})
@RequiredArgsConstructor
public class SystemDictController {

    private final DictCacheService dictCacheService;

    private final DictService dictService;



    @RequestMapping(path = {""}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取字典")
    public List<KeyValueResVO> getDict(String dictName) {
        return dictCacheService.getKeyValue(dictName);
    }

    @RequestMapping(path = {"/value"}, method = {RequestMethod.GET})
    public KeyValueResVO getDictByValue(String dictName, String value) {
        SysDict dict = dictCacheService.getDictByValue(dictName, value);
        return dictCacheService.buildKeyValueResVO(dict);
    }

    @RequestMapping(path = {"/label"}, method = {RequestMethod.GET})
    public KeyValueResVO getDictByLabel(String dictName, String label) {
        SysDict dict = dictCacheService.getDictByLabel(dictName, label);
        return dictCacheService.buildKeyValueResVO(dict);
    }

    @ApiOperation("根据字典条目中文名查询")
    @RequestMapping(path = {"/list/label"}, method = {RequestMethod.GET})
    public List<KeyValueResVO> getDictListByLabel(String dictName, String name) {
        return dictService.getSysDictByLabel(dictName, name);
    }
}
