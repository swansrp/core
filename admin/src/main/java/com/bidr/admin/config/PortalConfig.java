package com.bidr.admin.config;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.portal.PortalFieldDict;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
    @Value("${my.base-package}")
    private String basePackage;

    @Override
    public void run(String... args) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> portalControllerList = reflections.getTypesAnnotatedWith(AdminPortal.class);
        Map<Class<?>, Collection<Field>> map;
        if (FuncUtil.isNotEmpty(portalControllerList)) {
            map = new LinkedHashMap<>(portalControllerList.size());
            TreeSet<Class<?>> portalControllerSet = new TreeSet<>(
                    (o1, o2) -> StringUtils.compare(o1.getName(), o2.getName()));
            portalControllerSet.addAll(portalControllerList);
            for (Class<?> portalControllerClass : portalControllerSet) {
                if (AdminControllerInf.class.isAssignableFrom(portalControllerClass)) {
                    Class<?> entityClass = ReflectionUtil.getSuperClassGenericType(portalControllerClass, 0);
                    Class<?> voClass = ReflectionUtil.getSuperClassGenericType(portalControllerClass, 1);
                    if (FuncUtil.isNotEmpty(entityClass) && FuncUtil.isNotEmpty(voClass)) {
                        map.put(portalControllerClass, ReflectionUtil.getFieldMap(voClass).values());
                    }
                }
            }
            refreshPortalConfig(map);
        }

    }

    private void refreshPortalConfig(Map<Class<?>, Collection<Field>> map) {
        for (Map.Entry<Class<?>, Collection<Field>> entry : map.entrySet()) {
            Class<?> entityClass = ReflectionUtil.getSuperClassGenericType(entry.getKey(), 0);
            AdminPortal adminPortal = entry.getKey().getAnnotation(AdminPortal.class);
            ApiModel apiModel = entityClass.getAnnotation(ApiModel.class);
            List<SysPortal> portalList = sysPortalService.getByBeanName(entry.getKey().getName());
            if (FuncUtil.isEmpty(portalList)) {
                SysPortal portal = new SysPortal();
                portal.setBean(entry.getKey().getName());
                if (FuncUtil.isNotEmpty(adminPortal.value())) {
                    portal.setName(adminPortal.value());
                } else {
                    portal.setName(entityClass.getSimpleName());
                }
                if (FuncUtil.isNotEmpty(apiModel)) {
                    portal.setDisplayName(apiModel.description());
                } else {
                    portal.setDisplayName(entityClass.getSimpleName());
                }
                portal.setDisplayName(portal.getDisplayName() + "(默认)");
                portal.setUrl(entityClass.getSimpleName());
                sysPortalService.save(portal);
                portalList.add(portal);
            }
            for (SysPortal portal : portalList) {
                if (FuncUtil.isNotEmpty(entry.getValue())) {
                    int order = 0;
                    List<SysPortalColumn> columnList = sysPortalColumnService.getPropertyListByPortalId(portal.getId());
                    Map<String, SysPortalColumn> columnMap = ReflectionUtil.reflectToMap(columnList,
                            SysPortalColumn::getProperty);
                    Set<String> propertyListSaved = new HashSet<>();
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
                            PortalNoFilterField portalNoFilterField = field.getAnnotation(PortalNoFilterField.class);
                            if (FuncUtil.isNotEmpty(portalNoFilterField)) {
                                column.setFilterAble(CommonConst.NO);
                            } else {
                                column.setFilterAble(CommonConst.YES);
                            }
                            sysPortalColumnService.insertOrUpdate(column);
                        } else {
                            propertyListSaved.add(field.getName());
                        }
                    }
                    if (FuncUtil.isNotEmpty(propertyListSaved)) {
                        for (String field : propertyListSaved) {
                            columnMap.remove(field);
                        }
                        sysPortalColumnService.deleteEntities(
                                ReflectionUtil.getFieldList(columnMap.values(), SysPortalColumn::getId));
                    }

                }
            }
        }
    }
}
