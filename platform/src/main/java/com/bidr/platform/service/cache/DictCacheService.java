package com.bidr.platform.service.cache;

import cn.hutool.core.collection.CollectionUtil;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.constant.err.DictErrorCode;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.dao.repository.SysDictTypeService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.*;

/**
 * Title: DictCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 13:22
 */
@Service
public class DictCacheService extends DynamicMemoryCache<Map<String, LinkedHashMap<String, SysDict>>> {
    @Resource
    private SysDictService sysDictService;
    @Resource
    private SysDictTypeService sysDictTypeService;
    @Resource
    private WebApplicationContext webApplicationContext;

    @Lazy
    @Resource
    private DictCacheService self;

    @Override
    protected Collection<Map<String, LinkedHashMap<String, SysDict>>> getCacheData() {
        List<SysDict> sysDictCache = sysDictService.getSysDictCache();
        addDictEnum(sysDictCache);
        Map<String, Map<String, LinkedHashMap<String, SysDict>>> resMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(sysDictCache)) {
            for (SysDict dict : sysDictCache) {
                Map<String, LinkedHashMap<String, SysDict>> map = resMap.getOrDefault(dict.getDictName(),
                        new HashMap<>());
                for (DictTypeEnum value : DictTypeEnum.values()) {
                    LinkedHashMap<String, SysDict> typeMap = map.getOrDefault(value.name(), new LinkedHashMap<>());
                    typeMap.put(value.getFunc.apply(dict), dict);
                    map.put(value.name(), typeMap);

                }
                resMap.put(dict.getDictName(), map);
            }
        }
        return resMap.values();
    }

    @SuppressWarnings("rawtypes")
    private void addDictEnum(List<SysDict> list) {
        Set<String> sysDictInDb = new TreeSet<>();
        if (CollectionUtils.isNotEmpty(list)) {
            for (SysDict sysDict : list) {
                sysDictInDb.add(StringUtil.join(sysDict.getDictName(), sysDict.getDictValue()));
            }
        }
        List<SysDictType> sysDictTypeList = sysDictTypeService.list();
        Set<SysDictType> sysDictTypeSet = new HashSet<>();
        if (CollectionUtils.isNotEmpty(sysDictTypeList)) {
            sysDictTypeSet.addAll(sysDictTypeList);
        }
        Set<SysDictType> syncSysDictTypeSet = new HashSet<>();
        Set<SysDict> syncSysDictSet = new HashSet<>();
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<?>> metaDictClass = reflections.getTypesAnnotatedWith(MetaDict.class);
        for (Class<?> clazz : metaDictClass) {
            if (Enum.class.isAssignableFrom(clazz) && Dict.class.isAssignableFrom(clazz) &&
                    clazz.isAnnotationPresent(MetaDict.class)) {
                String dictName = clazz.getAnnotation(MetaDict.class).value();
                String dictTitle = clazz.getAnnotation(MetaDict.class).remark();
                SysDictType sysDictType = buildSysDictType(dictName, dictTitle);
                if (!sysDictTypeSet.contains(sysDictType)) {
                    syncSysDictTypeSet.add(sysDictType);
                }
                for (Object enumItem : clazz.getEnumConstants()) {
                    SysDict item = buildSysDict(dictName, dictTitle, enumItem);
                    if (!(sysDictInDb.contains(StringUtil.join(item.getDictName(), item.getDictValue())))) {
                        syncSysDictSet.add(item);
                        list.add(item);
                    }
                }
            } else if (IDynamicDict.class.isAssignableFrom(clazz) && clazz.isAnnotationPresent(MetaDict.class)) {
                String dictName = clazz.getAnnotation(MetaDict.class).value();
                String dictTitle = clazz.getAnnotation(MetaDict.class).remark();
                SysDictType sysDictType = buildSysDictType(dictName, dictTitle);
                if (!sysDictTypeSet.contains(sysDictType)) {
                    syncSysDictTypeSet.add(sysDictType);
                }
                try {
                    IDynamicDict dynamicDictService = (IDynamicDict) webApplicationContext.getBean(clazz);
                    List<SysDict> dynamicDictList = dynamicDictService.generate();
                    CollectionUtil.sort(dynamicDictList, Comparator.comparingInt(SysDict::getDictSort));
                    if (CollectionUtils.isNotEmpty(dynamicDictList)) {
                        list.addAll(dynamicDictList);
                    }
                } catch (BeansException e) {
                    e.printStackTrace();
                }
            }
        }
        syncSysDictType(syncSysDictTypeSet);
        syncSysDict(syncSysDictSet);
    }

    private SysDictType buildSysDictType(String dictName, String dictTitle) {
        SysDictType sysDictType = new SysDictType();
        sysDictType.setDictName(dictName);
        sysDictType.setDictTitle(dictTitle);
        return sysDictType;
    }

    private SysDict buildSysDict(String dictName, String title, Object enumItem) {
        SysDict item = new SysDict();
        String itemId = ((Enum) enumItem).name();
        Integer order = ((Enum) enumItem).ordinal();
        item.setDictName(dictName);
        item.setDictTitle(title);
        item.setDictItem(itemId);
        Dict dict = (Dict) enumItem;
        item.setDictValue(dict.getValue().toString());
        item.setDictLabel(dict.getLabel());
        item.setShow(dict.getShow());
        item.setDictSort(order);
        item.setStatus(CommonConst.YES);
        item.setRemark(ReflectionUtil.getValue(enumItem, "label", String.class));
        return item;
    }

    private void syncSysDictType(Collection<SysDictType> sysDictType) {
        sysDictTypeService.saveBatch(sysDictType);
    }

    private void syncSysDict(Collection<SysDict> item) {
        sysDictService.saveBatch(item);
    }

    @Override
    protected Object getCacheKey(Map<String, LinkedHashMap<String, SysDict>> map) {
        LinkedHashMap<String, SysDict> dictMap = map.get(DictTypeEnum.VALUE.name());
        for (Map.Entry<String, SysDict> entry : dictMap.entrySet()) {
            return entry.getValue().getDictName();
        }
        return null;
    }

    public SysDict getDictByName(String dictName, String name) {
        return getDict(dictName, dictName, DictTypeEnum.NAME);
    }

    private SysDict getDict(String dictName, String dictValue, DictTypeEnum type) {
        Map<String, LinkedHashMap<String, SysDict>> cache = self.getCache(dictName);
        Validator.assertNotEmpty(cache, DictErrorCode.DICT_IS_NOT_EXISTED, dictName);
        SysDict res = cache.get(type.name()).get(dictValue);
        Validator.assertNotNull(res, type.getErrorCode(), dictName, dictValue);
        return res;
    }

    private String buildKey(DictTypeEnum type, String key) {
        return StringUtil.join(type.name(), key);
    }

    public SysDict getDictByValue(String dictName, String value) {
        return getDict(dictName, value, DictTypeEnum.VALUE);
    }

    public SysDict getDictByLabel(String dictName, String label) {
        return getDict(dictName, label, DictTypeEnum.LABEL);
    }

    public List<KeyValueResVO> getKeyValue(String dictName) {
        List<KeyValueResVO> resList = new ArrayList<>();
        Map<String, LinkedHashMap<String, SysDict>> cache = self.getCache(dictName);
        Validator.assertNotEmpty(cache, DictErrorCode.DICT_IS_NOT_EXISTED, dictName);
        LinkedHashMap<String, SysDict> valueMap = cache.get(DictTypeEnum.VALUE.name());
        valueMap.forEach((key, value) -> {
            KeyValueResVO res = new KeyValueResVO();
            res.setValue(key);
            res.setLabel(value.getDictLabel());
            resList.add(res);
        });
        return resList;
    }

    public KeyValueResVO buildKeyValueResVO(SysDict dict) {
        KeyValueResVO vo = new KeyValueResVO();
        vo.setValue(dict.getDictValue());
        vo.setLabel(dict.getDictLabel());
        return vo;
    }

    @Getter
    @AllArgsConstructor
    enum DictTypeEnum {
        /**
         * 字典缓存类型
         */
        NAME(DictErrorCode.DICT_NAME_IS_NOT_EXISTED, SysDict::getDictName),
        VALUE(DictErrorCode.DICT_VALUE_IS_NOT_EXISTED, SysDict::getDictValue),
        LABEL(DictErrorCode.DICT_LABEL_IS_NOT_EXISTED, SysDict::getDictLabel);

        private final DictErrorCode errorCode;
        private final GetFunc<SysDict, String> getFunc;
    }
}
