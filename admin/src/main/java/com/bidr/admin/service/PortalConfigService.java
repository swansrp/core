package com.bidr.admin.service;

import com.bidr.admin.config.PortalNoFilterField;
import com.bidr.admin.constant.token.PortalTokenItem;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.portal.PortalFieldDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.config.portal.AdminPortal;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Title: PortalConfigService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/22 09:11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalConfigService implements LoginFillTokenInf {

    public static final Long DEFAULT_CONFIG_ROLE_ID = 0L;
    private static final Map<Class<?>, PortalFieldDict> FIELD_MAP = new HashMap<>();
    private static final Map<Long, String> ROLE_BIND_PORTAL_MAP = new HashMap<>();

    static {
        FIELD_MAP.put(Boolean.class, PortalFieldDict.BOOLEAN);
        FIELD_MAP.put(String.class, PortalFieldDict.STRING);
        FIELD_MAP.put(Date.class, PortalFieldDict.DATE);
        FIELD_MAP.put(LocalDateTime.class, PortalFieldDict.DATE);
        FIELD_MAP.put(Integer.class, PortalFieldDict.NUMBER);
        FIELD_MAP.put(BigDecimal.class, PortalFieldDict.NUMBER);
        FIELD_MAP.put(Long.class, PortalFieldDict.NUMBER);
        FIELD_MAP.put(Double.class, PortalFieldDict.NUMBER);
        ROLE_BIND_PORTAL_MAP.put(DEFAULT_CONFIG_ROLE_ID, "默认配置");
    }

    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;
    private final TokenService tokenService;
    private final AcRoleService acRoleService;

    @Value("${my.base-package}")
    private String basePackage;

    @PostConstruct
    @Transactional(rollbackFor = Exception.class)
    public void init() {
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
        Map<Long, AcRole> roleCachedMap = new HashMap<>();
        for (Map.Entry<Class<?>, Collection<Field>> entry : map.entrySet()) {
            Class<?> entityClass = ReflectionUtil.getSuperClassGenericType(entry.getKey(), 0);
            List<SysPortal> portalList = sysPortalService.getByBeanName(entry.getKey().getName());
            if (FuncUtil.isEmpty(portalList)) {
                SysPortal portal = buildSysPortal(entry.getKey(), entityClass);
                sysPortalService.save(portal);
                portalList.add(portal);
            }
            for (SysPortal portal : portalList) {
                if (FuncUtil.isNotEmpty(entry.getValue())) {
                    refreshPortalColumn(entry.getValue(), portal, portal.getRoleId());
                    if (!ROLE_BIND_PORTAL_MAP.containsKey(portal.getRoleId())) {
                        if (!roleCachedMap.containsKey(portal.getRoleId())) {
                            AcRole role = acRoleService.selectById(portal.getRoleId());
                            roleCachedMap.put(portal.getRoleId(), role);
                        }
                        AcRole role = roleCachedMap.get(portal.getRoleId());
                        if (FuncUtil.isNotEmpty(role)) {
                            ROLE_BIND_PORTAL_MAP.put(portal.getRoleId(), role.getRoleName());
                        }

                    }
                }
            }
        }
    }

    private SysPortal buildSysPortal(Class<?> controllerClass, Class<?> entityClass) {
        AdminPortal adminPortal = controllerClass.getAnnotation(AdminPortal.class);
        ApiModel apiModel = entityClass.getAnnotation(ApiModel.class);
        SysPortal portal = new SysPortal();
        portal.setBean(controllerClass.getName());
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
        portal.setRoleId(DEFAULT_CONFIG_ROLE_ID);
        return portal;
    }

    private void refreshPortalColumn(Collection<Field> fields, SysPortal portal, Long roleId) {
        int order = 0;
        List<SysPortalColumn> columnList = sysPortalColumnService.getPropertyListByPortalId(portal.getId(), roleId);
        Map<String, SysPortalColumn> columnMap = ReflectionUtil.reflectToMap(columnList, SysPortalColumn::getProperty);
        Set<String> propertyListSaved = new HashSet<>();
        for (Field field : fields) {
            if (!columnMap.containsKey(field.getName())) {
                SysPortalColumn column = buildSysPortalColumn(portal, order++, field, roleId);
                sysPortalColumnService.insert(column);
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

    private SysPortalColumn buildSysPortalColumn(SysPortal portal, int order, Field field, Long roleId) {
        SysPortalColumn column = new SysPortalColumn();
        column.setPortalId(portal.getId());
        column.setProperty(field.getName());
        column.setDisplayOrder(order);
        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
        if (FuncUtil.isNotEmpty(apiModelProperty)) {
            column.setDisplayName(apiModelProperty.value());
        } else {
            column.setDisplayName(field.getName());
        }
        PortalFieldDict portalFieldDict = FIELD_MAP.getOrDefault(field.getType(), PortalFieldDict.STRING);
        column.setFieldType(portalFieldDict.getValue());
        PortalNoFilterField portalNoFilterField = field.getAnnotation(PortalNoFilterField.class);
        if (FuncUtil.isNotEmpty(portalNoFilterField)) {
            column.setFilterAble(CommonConst.NO);
        } else {
            column.setFilterAble(CommonConst.YES);
        }
        column.setRoleId(roleId);
        return column;
    }

    @Override
    public void fillToken(LoginRes token) {
        Map<String, Object> map = tokenService.getTokenValue();
        Map<Long, RoleInfo> roleInfoMap = JsonUtil.readJson(map.get(TokenItem.ROLE_MAP.name()), Map.class, Long.class,
                RoleInfo.class);
        for (Long roleId : roleInfoMap.keySet()) {
            if (ROLE_BIND_PORTAL_MAP.containsKey(roleId)) {
                tokenService.putItem(PortalTokenItem.PORTAL_ROLE, roleId);
                log.info("实体显示配置使用[{}]方案", roleInfoMap.get(roleId).getRoleName());
                return;
            }
        }
        tokenService.putItem(PortalTokenItem.PORTAL_ROLE, DEFAULT_CONFIG_ROLE_ID);
    }

    public List<KeyValueResVO> getBindRoleDict() {
        List<KeyValueResVO> resList = new ArrayList<>();
        for (Map.Entry<Long, String> entry : ROLE_BIND_PORTAL_MAP.entrySet()) {
            if (FuncUtil.isNotEmpty(entry.getValue())) {
                resList.add(new KeyValueResVO(entry.getKey().toString(), entry.getValue()));
            }
        }
        return resList;
    }

    public Collection<Long> getBindRoleIdList() {
        return ROLE_BIND_PORTAL_MAP.keySet();
    }


    @Transactional(rollbackFor = Exception.class)
    public void bindRole(Long roleId, Long templateRoleId) {
        if (FuncUtil.isEmpty(templateRoleId)) {
            templateRoleId = DEFAULT_CONFIG_ROLE_ID;
        }
        List<PortalWithColumnsRes> templatePortalList = sysPortalService.getPortalWithColumnsByRoleId(templateRoleId);
        Validator.assertNotEmpty(templatePortalList, ErrCodeSys.PA_DATA_NOT_EXIST, "模版配置");
        AcRole role = acRoleService.selectById(roleId);
        Validator.assertNotNull(role, ErrCodeSys.PA_DATA_NOT_EXIST, "角色");
        unBindRole(roleId);
        for (PortalWithColumnsRes portal : templatePortalList) {
            portal.setId(null);
            portal.setRoleId(roleId);
            sysPortalService.insert(portal);
            if (FuncUtil.isNotEmpty(portal.getColumns())) {
                for (SysPortalColumn column : portal.getColumns()) {
                    column.setPortalId(portal.getId());
                    column.setRoleId(roleId);
                    column.setId(null);
                }
                sysPortalColumnService.insert(portal.getColumns());
            }
        }
        ROLE_BIND_PORTAL_MAP.put(roleId, role.getRoleName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unBindRole(Long roleId) {
        sysPortalService.deleteByRoleId(roleId);
        sysPortalColumnService.deleteByRoleId(roleId);
        ROLE_BIND_PORTAL_MAP.remove(roleId);
    }
}
