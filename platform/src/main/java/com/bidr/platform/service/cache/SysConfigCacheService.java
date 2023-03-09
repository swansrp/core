package com.bidr.platform.service.cache;

import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.repository.SysConfigService;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Override
    protected List<SysConfig> getCacheData() {
        List<SysConfig> list = sysConfigService.getSysConfigCache();
        addDefaultParameter(list);
        return list;
    }

    @SuppressWarnings("rawtypes")
    private void addDefaultParameter(List<SysConfig> list) {
        Map<String, SysConfig> map = ReflectionUtil.reflectToMap(list, "configName");
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<?>> metaParamClass = reflections.getTypesAnnotatedWith(MetaParam.class);
        for (Class<?> clazz : metaParamClass) {
            if (Enum.class.isAssignableFrom(clazz) && Param.class.isAssignableFrom(clazz)) {
                for (Object enumItem : clazz.getEnumConstants()) {
                    String paramId = ((Enum) enumItem).name();
                    if (map.get(paramId) == null) {
                        Param param = (Param) enumItem;
                        SysConfig item = new SysConfig();
                        item.setConfigName(clazz.getAnnotation(MetaDict.class).value());
                        item.setConfigKey(paramId);
                        item.setConfigValue(param.getDefaultValue());
                        item.setRemark(param.getRemark());
                        list.add(item);
                    }
                }
            }
        }
    }

    @Override
    protected Object getCacheKey(SysConfig config) {
        return config.getConfigName() + config.getConfigKey();
    }

}
