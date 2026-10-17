package com.bidr.platform.service.cache.dict;

import cn.hutool.core.collection.CollectionUtil;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.cache.config.DynamicMemoryCacheManager;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
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
import java.util.stream.Collectors;

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
        // 类型行只允许由代码声明的字典（@MetaDict）回写：数据驱动型字典的 sys_dict_type 记录
        // 是用户在界面上维护的正源，回写会把标题/只读标记按启动时的快照覆盖回去
        if (isCodeGenerated()) {
            syncSysDictType(buildSysDictType(config.getDictName(), config.getDictTitle()));
        }
    }

    /**
     * 是否代码生成的字典（@MetaDict 枚举 / IDynamicDict），其内容以代码为准、开机重建；
     * 反之 read_only != '1' 的数据驱动型字典，条目与类型都以 sys_dict / sys_dict_type 为准。
     */
    private boolean isCodeGenerated() {
        return StringUtil.convertSwitch(config.getReadOnly());
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
        return getCacheData(false);
    }

    /**
     * 获取缓存数据
     *
     * @param init true=同步写入数据库（仅初始化时)，false=只读取不写库（刷新时)
     */
    @Override
    protected Map<String, LinkedHashMap<String, SysDict>> getCacheData(boolean init) {
        List<SysDict> sysDictCache = new ArrayList<>();
        String dictName = config.getDictName();
        String dictTitle = config.getDictTitle();

        // 1. 从代码/数据库读取数据
        if (StringUtil.convertSwitch(config.getReadOnly())) {
            if (config.getDynamic()) {
                buildDynamicSysDictCacheList(sysDictCache);
            } else {
                buildEnumSysDictCacheList(sysDictCache, dictName, dictTitle);
            }
        } else {
            buildDbSysDictCacheList(sysDictCache);
        }

        // 2. 构建内存映射
        Map<String, LinkedHashMap<String, SysDict>> map = new HashMap<>(DictTypeEnum.values().length);
        if (CollectionUtils.isNotEmpty(sysDictCache)) {
            buildSysDictMap(sysDictCache, map);
        }

        // 3. 写库：仅在初始化时执行（init=true）
        //    - 初始化：可能是首次部署，需要创建/更新数据库中的字典记录
        //    - 刷新：说明本地+Redis都过期了，但数据库已有数据，无需重复写入
        //    数据驱动型字典的数据正源就是 sys_dict，回写自己无意义，只做代码生成型
        if (init && isCodeGenerated()) {
            SysDictType sysDictType = buildSysDictType(dictName, dictTitle);
            syncSysDictType(sysDictType);
            if (CollectionUtils.isNotEmpty(sysDictCache)) {
                syncSysDict(dictName, sysDictCache);
            }
            log.debug("字典[{}]初始化完成，已写入数据库", dictName);
        }

        return map;
    }

    private SysDictType buildSysDictType(String dictName, String dictTitle) {
        SysDictType sysDictType = new SysDictType();
        sysDictType.setDictName(dictName);
        sysDictType.setDictTitle(dictTitle);
        // 必须跟随 config：这里曾硬编码 '1'，导致数据驱动型（手工添加，read_only='0'）字典
        // 被注册成 provider 的那次启动顺手改写成 '1'，下次启动 getNotReadOnlySysDictType 查不到它，
        // 不再注册缓存 → 手工字典活不过第二次重启
        sysDictType.setReadOnly(config.getReadOnly());
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
        // 使用更安全的同步策略，避免并发死锁
        // 原模式 delete + insert 在高并发下容易产生死锁：
        //   请求A: delete获取锁 -> 请求B: delete等待 -> 请求A: insert等待新锁 -> 死锁
        // 
        // 新策略：先查出需要删除的ID，按ID删除（避免大范围行锁），再批量upsert
        if (FuncUtil.isEmpty(sysDictList)) {
            sysDictService.deleteByDictName(dictName);
            return;
        }

        // 查询现有记录
        List<SysDict> existingList = sysDictService.getSysDictByName(dictName);

        // 构建新记录的ID集合
        Set<String> newIds = sysDictList.stream()
                .map(SysDict::getDictId)
                .collect(Collectors.toSet());

        // 找出需要删除的记录（存在于旧记录但不在新记录中）
        List<String> idsToDelete = existingList.stream()
                .map(SysDict::getDictId)
                .filter(id -> !newIds.contains(id))
                .collect(Collectors.toList());

        // 按ID删除（更精确的锁，减少死锁概率）
        if (FuncUtil.isNotEmpty(idsToDelete)) {
            sysDictService.removeBatchByIds(new ArrayList<>(idsToDelete));
        }

        // 批量插入或更新
        sysDictService.insertOrUpdate(sysDictList);
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
