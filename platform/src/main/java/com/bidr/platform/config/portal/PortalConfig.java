package com.bidr.platform.config.portal;

import com.bidr.kernel.constant.dict.portal.PortalFieldDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.dao.entity.SysPortal;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.bidr.platform.dao.repository.SysPortalColumnService;
import com.bidr.platform.dao.repository.SysPortalService;
import io.swagger.annotations.ApiModelProperty;
import lombok.RequiredArgsConstructor;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Title: PortalConfig
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/22 09:11
 */
@Service
@RequiredArgsConstructor
public class PortalConfig implements CommandLineRunner {

    @Value("${my.base-package}")
    private String basePackage;


    private static final Map<Class<?>, PortalFieldDict> FIELD_MAP = new HashMap<>();

    static {
        FIELD_MAP.put(String.class, PortalFieldDict.STRING);
        FIELD_MAP.put(Date.class, PortalFieldDict.DATE);
        FIELD_MAP.put(LocalDateTime.class, PortalFieldDict.STRING);
        FIELD_MAP.put(Integer.class, PortalFieldDict.NUMBER);
        FIELD_MAP.put(Boolean.class, PortalFieldDict.BOOLEAN);
        FIELD_MAP.put(BigDecimal.class, PortalFieldDict.NUMBER);
        FIELD_MAP.put(Long.class, PortalFieldDict.NUMBER);
        FIELD_MAP.put(Double.class, PortalFieldDict.NUMBER);
    }

    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;

    @Override
    public void run(String... args) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> portalEntityList = reflections.getTypesAnnotatedWith(PortalEntity.class);
        Map<String, List<Field>> map;
        if (FuncUtil.isNotEmpty(portalEntityList)) {
            map = new LinkedHashMap<>(portalEntityList.size());
            for (Class<?> aClass : portalEntityList) {
                for (String value : aClass.getAnnotation(PortalEntity.class).value()) {
                    map.put(value, ReflectionUtil.getFields(aClass));
                }
            }
            refreshPortalConfig(map);
        }

    }

    private void refreshPortalConfig(Map<String, List<Field>> map) {
        for (Map.Entry<String, List<Field>> entry : map.entrySet()) {
            SysPortal portal = sysPortalService.getByName(entry.getKey());
            if (FuncUtil.isEmpty(portal)) {
                portal = new SysPortal();
                portal.setName(entry.getKey());
                portal.setDisplayName(entry.getKey());
                portal.setUrl(entry.getKey());
                sysPortalService.save(portal);
            }
            if (FuncUtil.isNotEmpty(entry.getValue())) {
                int order = 0;
                List<SysPortalColumn> columnList = sysPortalColumnService.getPropertyListByPortalId(portal.getId());
                Map<String, SysPortalColumn> columnMap = ReflectionUtil.reflectToMap(columnList,
                        SysPortalColumn::getProperty);
                for (Field field : entry.getValue()) {
                    if (!columnMap.containsKey(field.getName())) {
                        SysPortalColumn column = new SysPortalColumn();
                        column.setPortalId(portal.getId());
                        column.setProperty(field.getName());
                        column.setDisplayOrder(order++);
                        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
                        if (FuncUtil.isNotEmpty(apiModelProperty)) {
                            column.setDisplayName(apiModelProperty.value());
                        } else {
                            column.setDisplayName(field.getName());
                        }
                        PortalFieldDict portalFieldDict = FIELD_MAP.get(field.getType());
                        if (FuncUtil.isEmpty(portalFieldDict)) {
                            column.setFieldType(PortalFieldDict.STRING.getValue());
                        } else {
                            column.setFieldType(portalFieldDict.getValue());
                        }
                        sysPortalColumnService.insert(column);
                    }
                }
            }
        }
    }
}
