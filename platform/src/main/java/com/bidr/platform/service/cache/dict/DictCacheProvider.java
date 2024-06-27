package com.bidr.platform.service.cache.dict;

import cn.hutool.core.collection.CollectionUtil;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.dao.repository.SysDictTypeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeansException;

import java.util.*;

/**
 * Title: DictCacheProvider
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 10:33
 */
@Slf4j
public class DictCacheProvider extends DynamicMemoryCache<LinkedHashMap<String, SysDict>> {

    private final DynamicMemoryCacheManager dynamicMemoryCacheManager;
    private final DictCacheConfig config;
    private final SysDictService sysDictService;
    private final SysDictTypeService sysDictTypeService;


    public DictCacheProvider(DynamicMemoryCacheManager dynamicMemoryCacheManager, DictCacheConfig config,
                             SysDictService sysDictService, SysDictTypeService sysDictTypeService, Boolean init) {
        this.dynamicMemoryCacheManager = dynamicMemoryCacheManager;
        this.config = config;
        this.sysDictService = sysDictService;
        this.sysDictTypeService = sysDictTypeService;
        Map<String, Integer> cacheConfig = new HashMap<>();
        cacheConfig.put(config.getDictName(), config.getExpired());
        this.dynamicMemoryCacheManager.addCache(cacheConfig);
        if (init) {
            this.init();
        }
        syncSysDictType(buildSysDictType(config.getDictName(), config.getDictTitle()));
    }

    private void buildSysDictMap(List<SysDict> sysDictCache, Map<String, LinkedHashMap<String, SysDict>> map) {
        for (SysDict dict : sysDictCache) {
            for (DictTypeEnum value : DictTypeEnum.values()) {
                LinkedHashMap<String, SysDict> typeMap = map.getOrDefault(value.name(), new LinkedHashMap<>());
                typeMap.put(value.getGetFunc().apply(dict), dict);
                map.put(value.name(), typeMap);
            }
        }
    }

    @Override
    public DynamicMemoryCacheManager cacheManager() {
        return dynamicMemoryCacheManager;
    }

    @Override
    protected Map<String, LinkedHashMap<String, SysDict>> getCacheData() {
        List<SysDict> sysDictCache = new ArrayList<>();
        String dictName = config.getDictName();
        String dictTitle = config.getDictTitle();
        SysDictType sysDictType = buildSysDictType(dictName, dictTitle);
        if (StringUtil.convertSwitch(config.getReadOnly())) {
            if (config.getDynamic()) {
                buildDynamicSysDictCacheList(sysDictCache);
            } else {
                buildEnumSysDictCacheList(sysDictCache, dictName, dictTitle);
            }
        } else {
            buildDbSysDictCacheList(sysDictCache);
        }
        Map<String, LinkedHashMap<String, SysDict>> map = new HashMap<>(DictTypeEnum.values().length);
        if (CollectionUtils.isNotEmpty(sysDictCache)) {
            buildSysDictMap(sysDictCache, map);
            syncSysDictType(sysDictType);
            syncSysDict(config.getDictName(), sysDictCache);
        }
        return map;

    }

    private SysDictType buildSysDictType(String dictName, String dictTitle) {
        SysDictType sysDictType = new SysDictType();
        sysDictType.setDictName(dictName);
        sysDictType.setDictTitle(dictTitle);
        sysDictType.setReadOnly(CommonConst.YES);
        return sysDictType;
    }

    private void buildDynamicSysDictCacheList(List<SysDict> sysDictCache) {
        try {
            IDynamicDict dynamicDictService = (IDynamicDict) BeanUtil.getBean(config.getDictClazz());
            Collection<SysDict> dynamicDictList = dynamicDictService.generate();
            CollectionUtil.sort(dynamicDictList, Comparator.comparingInt(SysDict::getDictSort));
            if (CollectionUtils.isNotEmpty(dynamicDictList)) {
                sysDictCache.addAll(dynamicDictList);
            }
        } catch (BeansException e) {
            log.error("", e);
        }
    }

    private void buildEnumSysDictCacheList(List<SysDict> sysDictCache, String dictName, String dictTitle) {
        Validator.assertNotNull(config.getDictClazz(), ErrCodeSys.SYS_CONFIG_NOT_EXIST, "字典配置: " + dictTitle);
        for (Object enumItem : config.getDictClazz().getEnumConstants()) {
            SysDict item = buildSysDict(dictName, dictTitle, enumItem);
            sysDictCache.add(item);
        }
    }

    private void buildDbSysDictCacheList(List<SysDict> sysDictCache) {
        sysDictCache.addAll(sysDictService.getSysDictByName(config.getDictName()));
    }

    private void syncSysDictType(SysDictType sysDictType) {
        sysDictTypeService.insertOrUpdate(sysDictType);
    }

    private void syncSysDict(String dictName, Collection<SysDict> sysDictList) {
        sysDictService.deleteByDictName(dictName);
        sysDictService.saveBatch(sysDictList);
    }

    private SysDict buildSysDict(String dictName, String title, Object enumItem) {
        SysDict item = new SysDict();
        String itemId = ((Enum) enumItem).name();
        Integer order = ((Enum) enumItem).ordinal();
        item.setDictId(dictName + "_" + itemId);
        item.setDictName(dictName);
        item.setDictTitle(title);
        item.setDictItem(itemId);
        Dict dict = (Dict) enumItem;
        item.setDictValue(dict.getValue().toString());
        item.setDictLabel(dict.getLabel());
        item.setShow(dict.getShow());
        item.setDictSort(order);
        item.setStatus(CommonConst.YES);
        item.setReadOnly(CommonConst.YES);
        item.setRemark(ReflectionUtil.getValue(enumItem, "label", String.class));
        return item;
    }

    @Override
    public String getCacheName() {
        return config.getDictName();
    }
}
