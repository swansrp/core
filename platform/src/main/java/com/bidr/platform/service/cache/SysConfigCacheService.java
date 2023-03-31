package com.bidr.platform.service.cache;

import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.constant.err.ConfigErrorCode;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.repository.SysConfigService;
import org.reflections.Reflections;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * Title: SysConfigCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/09 11:41
 */
@Service
public class SysConfigCacheService extends DynamicMemoryCache<SysConfig> {
    @Resource
    private SysConfigService sysConfigService;
    @Lazy
    @Resource
    private SysConfigCacheService self;

    @Override
    protected Collection<SysConfig> getCacheData() {
        List<SysConfig> list = sysConfigService.getSysConfigCache();
        addDefaultParameter(list);
        return list;
    }

    @SuppressWarnings("rawtypes")
    private void addDefaultParameter(List<SysConfig> list) {
        Map<String, SysConfig> map = ReflectionUtil.reflectToMap(list, "configKey");
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<?>> metaParamClass = reflections.getTypesAnnotatedWith(MetaParam.class);
        for (Class<?> clazz : metaParamClass) {
            if (Enum.class.isAssignableFrom(clazz) && Param.class.isAssignableFrom(clazz)) {
                for (Object enumItem : clazz.getEnumConstants()) {
                    String configKey = ((Enum) enumItem).name();
                    if (map.get(configKey) == null) {
                        Param param = (Param) enumItem;
                        SysConfig item = buildSysConfig(clazz, configKey, param);
                        sysConfigService.insert(item);
                        list.add(item);
                    }
                }
            }
        }
    }

    private SysConfig buildSysConfig(Class<?> clazz, String configKey, Param param) {
        SysConfig item = new SysConfig();
        item.setConfigName(param.getTitle());
        item.setConfigKey(configKey);
        item.setConfigValue(param.getDefaultValue());
        item.setRemark(param.getRemark());
        item.setCreateTime(new Date());
        item.setUpdateTime(new Date());
        return item;
    }

    @Override
    protected Object getCacheKey(SysConfig config) {
        return config.getConfigKey();
    }

    public Boolean getSysConfigBool(Param param) {
        return StringUtil.convertSwitch(getSysConfigValue(param));
    }

    public String getSysConfigValue(Param param) {
        SysConfig cache = self.getCache(param.name());
        Validator.assertNotNull(cache, ConfigErrorCode.PARAM_IS_NOT_EXISTED, param.getTitle());
        return cache.getConfigValue();
    }

    public Boolean getSysConfigBool(String configKey) {
        return StringUtil.convertSwitch(getSysConfigValue(configKey));
    }

    public String getSysConfigValue(String configKey) {
        SysConfig cache = self.getCache(configKey);
        Validator.assertNotNull(cache, ConfigErrorCode.PARAM_IS_NOT_EXISTED, configKey);
        return cache.getConfigValue();
    }

}
