package com.bidr.platform.controller;

import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDynamicDictConfig;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.service.dict.DictService;
import com.bidr.platform.service.dict.DynamicDictService;
import com.bidr.platform.vo.dict.DeleteDynamicDictConfigReq;
import com.bidr.platform.vo.dict.DynamicDictItemVO;
import com.bidr.platform.vo.dict.DynamicDictReq;
import com.bidr.kernel.config.response.Resp;
import com.bidr.platform.vo.dict.DictRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    private final DynamicDictService dynamicDictService;


    @RequestMapping(path = {""}, method = {RequestMethod.GET})
    @ApiOperation(value = "获取字典")
    public List<DictRes> getDict(String dictName) {
        return dictCacheService.getKeyValue(dictName);
    }

    @RequestMapping(path = {"/value"}, method = {RequestMethod.GET})
    public DictRes getDictByValue(String dictName, String value) {
        SysDict dict = dictCacheService.getDictByValue(dictName, value);
        return dictCacheService.buildKeyValueResVO(dict);
    }

    @RequestMapping(path = {"/label"}, method = {RequestMethod.GET})
    public DictRes getDictByLabel(String dictName, String label) {
        SysDict dict = dictCacheService.getDictByLabel(dictName, label);
        return dictCacheService.buildKeyValueResVO(dict);
    }

    @ApiOperation("根据字典条目中文名查询")
    @RequestMapping(path = {"/list/label"}, method = {RequestMethod.GET})
    public List<DictRes> getDictListByLabel(String dictName, String name) {
        return dictService.getSysDictByLabel(dictName, name);
    }

    /**
     * 动态生成字典选项
     * <p>
     * 从指定数据源的表中，通过 GROUP BY value 和 label 列，
     * 加上可选的筛选条件和排序，自动生成字典选项列表。
     *
     * @param req 动态字典请求（数据源、表名、value列、label列、排序、条件）
     * @return 字典选项列表
     */
    @ApiOperation("动态生成字典选项（预览查询）")
    @PostMapping("/dynamic")
    public List<DynamicDictItemVO> getDynamicDict(@RequestBody DynamicDictReq req) {
        return dynamicDictService.generateDict(req);
    }

    @ApiOperation("保存动态字典配置")
    @PostMapping("/dynamic/config")
    public void saveDynamicDictConfig(@RequestBody DynamicDictReq req) {
        dynamicDictService.saveConfig(req);
        Resp.notice("动态字典配置已保存并生效");
    }

    @ApiOperation("获取动态字典配置列表")
    @GetMapping("/dynamic/config")
    public List<SysDynamicDictConfig> getDynamicDictConfigList() {
        return dynamicDictService.getConfigList();
    }

    @ApiOperation("删除动态字典配置")
    @PostMapping("/dynamic/config/delete")
    public void deleteDynamicDictConfig(@RequestBody DeleteDynamicDictConfigReq req) {
        dynamicDictService.deleteConfig(req.getId());
        Resp.notice("动态字典配置已删除");
    }
}
