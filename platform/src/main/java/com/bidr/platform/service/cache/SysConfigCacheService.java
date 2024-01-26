package com.bidr.platform.service.cache;

import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.constant.err.ConfigErrorCode;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.repository.SysConfigService;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * Title: SysConfigCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 11:41
 */
@Service
public class SysConfigCacheService extends DynamicMemoryCache<SysConfig> {
    @Resource
    private SysConfigService sysConfigService;
    @Lazy
    @Resource
    private SysConfigCacheService self;
    @Value("${my.base-package}")
    private String basePackage;

    @Override
    protected Map<String, SysConfig> getCacheData() {
        List<SysConfig> list = sysConfigService.getSysConfigCache();
        addDefaultParameter(list);
        return ReflectionUtil.reflectToMap(list, SysConfig::getConfigKey);
    }

    @SuppressWarnings("rawtypes")
    private void addDefaultParameter(List<SysConfig> list) {
        Map<String, SysConfig> map = ReflectionUtil.reflectToMap(list, "configKey");
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> metaParamClass = reflections.getTypesAnnotatedWith(MetaParam.class);
        List<SysConfig> sysConfigList = new ArrayList<>();
        for (Class<?> clazz : metaParamClass) {
            if (Enum.class.isAssignableFrom(clazz) && Param.class.isAssignableFrom(clazz)) {
                for (Object enumItem : clazz.getEnumConstants()) {
                    String configKey = ((Enum) enumItem).name();
                    if (map.get(configKey) == null) {
                        Param param = (Param) enumItem;
                        SysConfig item = buildSysConfig(clazz, configKey, param);
                        sysConfigList.add(item);
                        list.add(item);
                    }
                }
            }
        }
        sysConfigService.saveBatch(sysConfigList);
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

    public Boolean getSysConfigBool(Param param) {
        return StringUtil.convertSwitch(getSysConfigValue(param));
    }

    public String getSysConfigValue(Param param) {
        SysConfig cache = self.getCache(param.name());
        Validator.assertNotNull(cache, ConfigErrorCode.PARAM_IS_NOT_EXISTED, param.getTitle());
        return cache.getConfigValue();
    }

    public boolean getParamSwitch(Param param) {
        return StringUtil.convertSwitch(getSysConfigValue(param));
    }

    public boolean getParamSwitch(String param) {
        return StringUtil.convertSwitch(getSysConfigValue(param));
    }

    public String getSysConfigValue(String param) {
        SysConfig cache = self.getCache(param);
        Validator.assertNotNull(cache, ConfigErrorCode.PARAM_IS_NOT_EXISTED, param);
        return cache.getConfigValue();
    }

    public String getParamValueAvail(Param param) {
        String res = getSysConfigValue(param);
        Validator.assertNotBlank(res, ErrCodeSys.PA_DATA_NOT_EXIST, param.getTitle());
        return res;
    }

    public String getParamValueAvail(String param) {
        String res = getSysConfigValue(param);
        Validator.assertNotBlank(res, ErrCodeSys.PA_DATA_NOT_EXIST, param);
        return res;
    }

    public int getParamInt(Param param) {
        return Integer.parseInt(getSysConfigValue(param));
    }

    public int getParamInt(String param) {
        return Integer.parseInt(getSysConfigValue(param));
    }

    public Long getParamLong(Param param) {
        return Long.parseLong(getSysConfigValue(param));
    }

    public Long getParamLong(String param) {
        return Long.parseLong(getSysConfigValue(param));
    }


}
