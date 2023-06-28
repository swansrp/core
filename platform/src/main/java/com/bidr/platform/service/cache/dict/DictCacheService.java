package com.bidr.platform.service.cache.dict;

import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: DictCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2022/12/30 13:22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictCacheService {

    private final Integer DEFAULT_EXPIRED = 24 * 60;

    private final SysDictTypeService sysDictTypeService;
    private final SysDictService sysDictService;
    private final DynamicMemoryCacheManager dynamicMemoryCacheManager;
    private final Map<String, DictCacheProvider> MAP = new ConcurrentHashMap<>();

    public SysDict getDictByName(String dictName, String name) {
        return getDict(dictName, dictName, DictTypeEnum.NAME);
    }

    private SysDict getDict(String dictName, String dictValue, DictTypeEnum type) {
        DictCacheProvider dictCacheProvider = MAP.get(dictName);
        Validator.assertNotNull(dictCacheProvider, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "字典: " + dictName);
        LinkedHashMap<String, SysDict> cache = dictCacheProvider.getCache(type.name());
        Validator.assertNotEmpty(cache, DictErrorCode.DICT_IS_NOT_EXISTED, dictName);
        SysDict res = cache.get(dictValue);
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
        DictCacheProvider dictCacheProvider = MAP.get(dictName);
        if (FuncUtil.isNotEmpty(dictCacheProvider)) {
            LinkedHashMap<String, SysDict> valueMap = dictCacheProvider.getCache(DictTypeEnum.VALUE.name());
            Validator.assertNotEmpty(valueMap, DictErrorCode.DICT_IS_NOT_EXISTED, dictName);
            valueMap.forEach((key, value) -> {
                KeyValueResVO res = new KeyValueResVO();
                res.setValue(key);
                res.setLabel(value.getDictLabel());
                resList.add(res);
            });
        }
        return resList;


    }

    public KeyValueResVO buildKeyValueResVO(SysDict dict) {
        KeyValueResVO vo = new KeyValueResVO();
        vo.setValue(dict.getDictValue());
        vo.setLabel(dict.getDictLabel());
        return vo;
    }

    public void refresh() {
        if (FuncUtil.isNotEmpty(MAP)) {
            for (Map.Entry<String, DictCacheProvider> entry : MAP.entrySet()) {
                entry.getValue().refresh();
            }
        } else {
            run();
        }
    }

    @PostConstruct
    public void run() {
        List<SysDictType> sysDictTypeList = sysDictTypeService.getNotReadOnlySysDictType();
        if (FuncUtil.isNotEmpty(sysDictTypeList)) {
            for (SysDictType sysDictType : sysDictTypeList) {
                DictCacheConfig config = ReflectionUtil.copy(sysDictType, DictCacheConfig.class);
                MAP.put(config.getDictName(),
                        new DictCacheProvider(dynamicMemoryCacheManager, config, sysDictService, sysDictTypeService,
                                false));
            }
        }
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<?>> metaDictClass = reflections.getTypesAnnotatedWith(MetaDict.class);

        for (Class<?> clazz : metaDictClass) {
            DictCacheConfig config;
            if (Enum.class.isAssignableFrom(clazz) && Dict.class.isAssignableFrom(clazz) &&
                    clazz.isAnnotationPresent(MetaDict.class)) {
                config = buildDictCacheConfig(clazz, false);
                MAP.put(config.getDictName(),
                        new DictCacheProvider(dynamicMemoryCacheManager, config, sysDictService, sysDictTypeService,
                                true));

            } else if (IDynamicDict.class.isAssignableFrom(clazz) && clazz.isAnnotationPresent(MetaDict.class)) {
                config = buildDictCacheConfig(clazz, true);
                MAP.put(config.getDictName(),
                        new DictCacheProvider(dynamicMemoryCacheManager, config, sysDictService, sysDictTypeService,
                                false));
            }
        }
    }

    private DictCacheConfig buildDictCacheConfig(Class<?> clazz, Boolean dynamic) {
        DictCacheConfig config = new DictCacheConfig();
        config.setDictClazz(clazz);
        String dictName = clazz.getAnnotation(MetaDict.class).value();
        String dictTitle = clazz.getAnnotation(MetaDict.class).remark();
        config.setDictName(dictName);
        config.setDictTitle(dictTitle);
        config.setDynamic(dynamic);
        config.setExpired(DEFAULT_EXPIRED);
        config.setReadOnly(CommonConst.YES);
        return config;
    }

    public void cachePrepare(String dictName) {
        DictCacheProvider dictCacheProvider = MAP.get(dictName);
        Validator.assertNotNull(dictCacheProvider, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "字典: " + dictName);
        dictCacheProvider.cachePrepare(dictName);
    }
}
