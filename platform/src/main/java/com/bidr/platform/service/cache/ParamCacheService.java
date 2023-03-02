package com.bidr.platform.service.cache;

import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.repository.SysConfigService;
import com.bidr.kernel.constant.param.Param;
import com.bidr.kernel.utils.ReflectionUtil;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Title: ParamCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 13:22
 */
@Service
public class ParamCacheService  {

    private static Map<String, SysConfig> cacheMap = new TreeMap<>();
    @Resource
    private SysConfigService service;

    public void init() {
//        List<SysConfig> sysConfigList = service.select();
//        addDefaultParameter(sysConfigList);
//        cacheMap = ReflectionUtil.reflectToMap(sysConfigList, "configKey");
    }

    private void addDefaultParameter(List<SysConfig> list) {
        Map<String, SysConfig> map = ReflectionUtil.reflectToMap(list, "configKey");
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<? extends Param>> paramClass = reflections.getSubTypesOf(Param.class);
        for (Class<?> clazz : paramClass) {
            if (Enum.class.isAssignableFrom(clazz)) {
                for (Object enumItem : clazz.getEnumConstants()) {
                    String paramId = ((Enum) enumItem).name();
                    if (map.get(paramId) == null) {
                        String valueObj = ReflectionUtil.getValue(enumItem, "defaultValue", String.class);
                        String remark = ReflectionUtil.getValue(enumItem, "remark", String.class);
                        SysConfig item = new SysConfig();
                        item.setConfigKey(paramId);
                        item.setConfigValue(valueObj);
                        item.setConfigName(remark);
                        list.add(item);
                    }
                }
            }
        }
    }


    public Map<String, SysConfig> getCache() {
        return cacheMap;
    }


    public void refresh() {
        init();
    }
}
