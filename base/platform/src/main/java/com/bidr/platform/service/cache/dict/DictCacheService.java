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
import com.bidr.platform.config.aop.RedisPublish;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.constant.err.DictErrorCode;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.dao.repository.SysDictService;
import com.bidr.platform.dao.repository.SysDictTypeService;
import com.bidr.platform.vo.dict.DictRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.bidr.kernel.utils.PackageScanUtil;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

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
public class DictCacheService implements CommandLineRunner {

    private final Integer DEFAULT_EXPIRED = 24 * 60;
    private final SysDictTypeService sysDictTypeService;
    private final SysDictService sysDictService;
    private final SysBizDictService sysBizDictService;
    private final DynamicMemoryCacheManager dynamicMemoryCacheManager;
    private final Map<String, DictCacheProvider> MAP = new ConcurrentHashMap<>();
    private final PlatformTransactionManager transactionManager;
    @Value("${my.base-package}")
    private String basePackage;

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

    public List<DictRes> getKeyValue(String dictName) {
        List<DictRes> resList = new ArrayList<>();
        DictCacheProvider dictCacheProvider = MAP.get(dictName);
        if (FuncUtil.isNotEmpty(dictCacheProvider)) {
            LinkedHashMap<String, SysDict> valueMap = dictCacheProvider.getCache(DictTypeEnum.VALUE.name());
            if (FuncUtil.isNotEmpty(valueMap)) {
                valueMap.forEach((key, value) -> {
                    DictRes res = new DictRes();
                    res.setValue(key);
                    res.setLabel(value.getDictLabel());
                    res.setShow(value.getShow());
                    resList.add(res);
                });
            }
        } else {
            List<SysBizDict> bizDictList = sysBizDictService.getBizDictItemsByCode(dictName);
            if (FuncUtil.isNotEmpty(bizDictList)) {
                bizDictList.forEach(dict -> {
                    DictRes res = new DictRes();
                    res.setValue(dict.getValue());
                    res.setLabel(dict.getLabel());
                    res.setShow(CommonConst.YES);
                    res.setParentValue(dict.getParentValue());
                    res.setParentDictCode(dict.getParentDictCode());
                    resList.add(res);
                });
            }
        }
        return resList;


    }

    public DictRes buildKeyValueResVO(SysDict dict) {
        DictRes vo = new DictRes();
        vo.setValue(dict.getDictValue());
        vo.setLabel(dict.getDictLabel());
        vo.setShow(dict.getShow());
        return vo;
    }

    public void refresh() {
        if (FuncUtil.isNotEmpty(MAP)) {
            for (Map.Entry<String, DictCacheProvider> entry : MAP.entrySet()) {
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionStatus status = transactionManager.getTransaction(def);
                try {
                    entry.getValue().refresh();
                    transactionManager.commit(status);
                } catch (Exception e) {
                    transactionManager.rollback(status);
                    // 处理异常
                }
            }
        } else {
            run(null);
        }
    }

    @Override
    public void run(String... args) {
        Reflections reflections = PackageScanUtil.reflections(basePackage);
        Set<Class<?>> metaDictClass = reflections.getTypesAnnotatedWith(MetaDict.class);

        // 开机以代码声明为准：先清掉已从代码里删除/改名、但数据库还残留的只读字典（类型行 + 条目）。
        // 这类残留原本没有任何对账方：provider 不再为其注册，syncSysDict 的差集删除也就永远不会执行
        cleanDeprecatedDictType(collectCodeDictName(metaDictClass));

        List<SysDictType> sysDictTypeList = sysDictTypeService.getNotReadOnlySysDictType();
        if (FuncUtil.isNotEmpty(sysDictTypeList)) {
            for (SysDictType sysDictType : sysDictTypeList) {
                DictCacheConfig config = ReflectionUtil.copy(sysDictType, DictCacheConfig.class);
                MAP.put(config.getDictName(),
                        new DictCacheProvider(dynamicMemoryCacheManager, config, sysDictService, sysDictTypeService,
                                false));
            }
        }

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
            } else {
                // 声明了 @MetaDict 但既不是 Dict 枚举也不是 IDynamicDict：原本会被静默丢弃，
                // 表现是界面上看得到字典名但下拉永远为空，这里留痕便于排查
                log.warn("字典[{}]未注册缓存：{} 既不是 Dict 枚举也不是 IDynamicDict",
                        clazz.getAnnotation(MetaDict.class).value(), clazz.getName());
            }
        }
    }

    /**
     * 收集本次启动代码声明的字典名（与 run() 中 provider 注册的判定口径保持一致）
     */
    private Set<String> collectCodeDictName(Set<Class<?>> metaDictClass) {
        Set<String> dictNameSet = new HashSet<>();
        for (Class<?> clazz : metaDictClass) {
            boolean codeDict = Enum.class.isAssignableFrom(clazz) && Dict.class.isAssignableFrom(clazz)
                    || IDynamicDict.class.isAssignableFrom(clazz);
            if (codeDict) {
                dictNameSet.add(clazz.getAnnotation(MetaDict.class).value());
            }
        }
        return dictNameSet;
    }

    private void cleanDeprecatedDictType(Set<String> codeDictNameSet) {
        List<SysDictType> deprecatedList = sysDictTypeService.getReadOnlyNotIn(codeDictNameSet);
        if (FuncUtil.isEmpty(deprecatedList)) {
            return;
        }
        List<String> dictNameList = ReflectionUtil.getFieldList(deprecatedList, SysDictType::getDictName);
        log.info("清理代码已删除的残留字典：{}", dictNameList);
        sysDictTypeService.deleteByDictNameList(dictNameList);
        sysDictService.deleteByDictList(dictNameList);
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

    /**
     * 注销字典缓存：界面删除数据驱动型字典类型后，MAP 里的 provider 若不清理，
     * 下拉仍会命中残留缓存直到过期或重启，表现为"删了还能选到"。
     */
    public void unregister(String dictName) {
        DictCacheProvider dictCacheProvider = MAP.remove(dictName);
        if (FuncUtil.isNotEmpty(dictCacheProvider)) {
            Cache cache = dynamicMemoryCacheManager.getCache(dictName);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @RedisPublish
    public void refresh(String dictName) {
        DictCacheProvider dictCacheProvider = MAP.get(dictName);
        Validator.assertNotNull(dictCacheProvider, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "字典: " + dictName);
        dictCacheProvider.refresh();
    }
}
