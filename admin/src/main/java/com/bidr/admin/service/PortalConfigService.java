package com.bidr.admin.service;

import com.alibaba.excel.annotation.ExcelProperty;
import com.bidr.admin.config.*;
import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.constant.token.PortalTokenItem;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalAssociate;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalAssociateService;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.vo.PortalReq;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.service.login.LoginFillTokenInf;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.common.convert.Convert;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.dict.MetaTreeDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.config.portal.AdminPortal;
import com.diboot.core.binding.annotation.BindEntity;
import com.diboot.core.binding.annotation.BindEntityList;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.binding.annotation.BindFieldList;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    private final ApplicationContext applicationContext;
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;
    private final SysPortalAssociateService sysPortalAssociateService;
    private final TokenService tokenService;
    private final AcRoleService acRoleService;
    private final PortalService portalService;

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
                Profile profileAnno = portalControllerClass.getAnnotation(Profile.class);
                if (FuncUtil.isNotEmpty(profileAnno)) {
                    if (!new HashSet<>(Arrays.asList(profileAnno.value())).containsAll(
                            Arrays.asList(applicationContext.getEnvironment().getActiveProfiles()))) {
                        continue;
                    }
                }
                if (AdminControllerInf.class.isAssignableFrom(portalControllerClass)) {
                    Class<?> entityClass = ReflectionUtil.getSuperClassGenericType(portalControllerClass, 0);
                    Class<?> voClass = ReflectionUtil.getSuperClassGenericType(portalControllerClass, 1);
                    if (FuncUtil.isNotEmpty(entityClass) && FuncUtil.isNotEmpty(voClass)) {
                        map.put(portalControllerClass, getFields(voClass));
                    }
                }
            }
            refreshPortalConfig(map);
        }

    }

    private List<Field> getFields(Class<?> clazz) {
        Collection<Field> fields = ReflectionUtil.reflectToLinkedMap(ReflectionUtil.getFields(clazz), Field::getName)
                .values();
        return fields.stream().filter(field -> {
            JsonIgnore annotation = field.getAnnotation(JsonIgnore.class);
            return annotation == null;
        }).collect(Collectors.toList());
    }

    private void refreshPortalConfig(Map<Class<?>, Collection<Field>> map) {
        Set<String> portalSet = new HashSet<>();
        Map<String, Class<?>> controllerMap = new HashMap<>(map.size());
        map.keySet().forEach((key) -> controllerMap.put(StringUtil.firstLowerCamelCase(key.getSimpleName()), key));
        Map<Long, AcRole> roleCachedMap = new HashMap<>();
        for (Map.Entry<Class<?>, Collection<Field>> entry : map.entrySet()) {
            SysPortal sysPortal = buildSysPortal(entry.getKey());
            Validator.assertTrue(portalSet.add(sysPortal.getName()), ErrCodeSys.SYS_ERR_MSG,
                    sysPortal.getName() + "portalId重复");
            List<SysPortal> portalList = sysPortalService.getByBeanName(sysPortal.getBean(), sysPortal.getName());
            if (FuncUtil.isEmpty(portalList)) {
                cleanInvalidPortalConfig(sysPortal.getName());
                sysPortalService.insert(sysPortal);
                portalList.add(sysPortal);
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

    private SysPortal buildSysPortal(Class<?> controllerClass) {
        Class<?> entityClass = ReflectionUtil.getSuperClassGenericType(controllerClass, 0);
        AdminPortal adminPortal = controllerClass.getAnnotation(AdminPortal.class);
        Api api = controllerClass.getAnnotation(Api.class);
        ApiModel apiModel = entityClass.getAnnotation(ApiModel.class);
        SysPortal portal = new SysPortal();
        RestController restController = controllerClass.getAnnotation(RestController.class);
        if (FuncUtil.isNotEmpty(restController) && FuncUtil.isNotEmpty(restController.value())) {
            portal.setBean(restController.value());
        } else {
            portal.setBean(StringUtil.firstLowerCamelCase(controllerClass.getSimpleName()));
        }
        if (FuncUtil.isNotEmpty(adminPortal.value())) {
            portal.setName(adminPortal.value());
        } else {
            portal.setName(entityClass.getSimpleName());
        }
        if (FuncUtil.isNotEmpty(api)) {
            if (FuncUtil.isNotEmpty(api.tags()) && FuncUtil.isNotEmpty(api.tags()[0])) {
                portal.setDisplayName(api.tags()[0]);
            } else if (FuncUtil.isNotEmpty(api.value())) {
                portal.setDisplayName(api.value());
            }
        } else {
            if (FuncUtil.isNotEmpty(apiModel)) {
                portal.setDisplayName(apiModel.description());
            } else {
                portal.setDisplayName(entityClass.getSimpleName());
            }
        }
        portal.setDisplayName(portal.getDisplayName() + "(默认)");
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (FuncUtil.isNotEmpty(requestMapping)) {
            if (FuncUtil.isNotEmpty(requestMapping.value())) {
                portal.setUrl(parseUrl(requestMapping.value()[0]));
            } else if (FuncUtil.isNotEmpty(requestMapping.path())) {
                portal.setUrl(parseUrl(requestMapping.path()[0]));
            }
        } else {
            portal.setUrl(entityClass.getSimpleName());
        }
        portal.setRoleId(DEFAULT_CONFIG_ROLE_ID);
        portal.setAdvanced(CommonConst.YES);
        portal.setIdColumn("auto_uuid_row_key");
        return portal;
    }

    private void cleanInvalidPortalConfig(String name) {
        SysPortal portal = sysPortalService.getByName(name, DEFAULT_CONFIG_ROLE_ID);
        if (FuncUtil.isNotEmpty(portal)) {
            // 仅删除ENTITY类型的配置，保留MATRIX和DATASET类型（动态创建的表）
            if (FuncUtil.isEmpty(portal.getDataMode())) {
                portalService.deletePortalConfig(new IdReqVO(portal.getName()));
            }
        }
    }

    private void refreshPortalColumn(Collection<Field> fields, SysPortal portal, Long roleId) {
        int order = 0;
        List<SysPortalColumn> columnList = sysPortalColumnService.getPropertyListByPortalId(portal.getId(), roleId);
        Map<String, SysPortalColumn> columnMap = ReflectionUtil.reflectToMap(columnList, SysPortalColumn::getProperty);
        Set<String> propertyListSaved = new HashSet<>();
        for (Field field : fields) {
            if (!columnMap.containsKey(field.getName())) {
                SysPortalColumn column = buildSysPortalColumn(portal, order, field, roleId);
                sysPortalColumnService.insert(column);
                sysPortalService.updateById(portal);
            } else {
                propertyListSaved.add(field.getName());
            }
            order++;
        }
        if (FuncUtil.isNotEmpty(propertyListSaved)) {
            for (String field : propertyListSaved) {
                columnMap.remove(field);
            }
            sysPortalColumnService.deleteEntities(
                    ReflectionUtil.getFieldList(columnMap.values(), SysPortalColumn::getId));
        }
    }

    private String parseUrl(String url) {
        return url.replace("/web/", "");
    }

    public SysPortalColumn buildSysPortalColumn(SysPortal portal, int order, Field field, Long roleId) {
        SysPortalColumn column = new SysPortalColumn();
        column.setPortalId(portal.getId());
        column.setProperty(field.getName());
        column.setDisplayOrder(order);
        handleDisplayName(field, column);
        PortalFieldDict portalFieldDict = FIELD_MAP.getOrDefault(field.getType(), PortalFieldDict.STRING);
        column.setFieldType(portalFieldDict.getValue());
        handlePortalField(portal, field, column);
        handlePortalEntityField(portal, field, column);
        // handlePortalDynamicColumnField(portal, field, column);
        handlePortalNoFilterField(field, column);
        handlePortalSortField(field, column);
        handlePortalDisplayOnlyField(field, column);
        handlePortalDisplayNoneField(field, column);
        handleGroupDisplayField(field, column);
        handleConvertField(field, column);
        handleBindField(field, column);
        handlePortalDictField(field, column);
        handlePortalMoneyField(field, column);
        handlePortalPercentField(field, column);
        handlePortalImageField(field, column);
        handlePortalAudioField(field, column);
        handlePortalTextAreaField(field, column);
        column.setRoleId(roleId);
        return column;
    }

    private void handleDisplayName(Field field, SysPortalColumn column) {
        column.setDisplayName(field.getName());
        ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
        if (FuncUtil.isNotEmpty(apiModelProperty)) {
            column.setDisplayName(apiModelProperty.value());
        }
        ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
        if (FuncUtil.isNotEmpty(excelProperty)) {
            column.setDisplayName(excelProperty.value()[0]);
        }
    }

    private void handlePortalField(SysPortal portal, Field field, SysPortalColumn column) {
        if (FuncUtil.isNotEmpty(field.getAnnotation(PortalIdField.class))) {
            portal.setIdColumn(field.getName());
            column.setShow(CommonConst.NO);
            column.setDetailShow(CommonConst.NO);
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
        }
        if (FuncUtil.isNotEmpty(field.getAnnotation(PortalNameField.class))) {
            portal.setNameColumn(field.getName());
            column.setWidth(0);
        }
        if (FuncUtil.isNotEmpty(field.getAnnotation(PortalPidField.class))) {
            portal.setPidColumn(field.getName());
            column.setShow(CommonConst.NO);
            column.setDetailShow(CommonConst.NO);
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
        }
        if (FuncUtil.isNotEmpty(field.getAnnotation(PortalOrderField.class))) {
            portal.setOrderColumn(field.getName());
            column.setShow(CommonConst.NO);
            column.setDetailShow(CommonConst.NO);
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
        }
    }

    private void handlePortalEntityField(SysPortal portal, Field field, SysPortalColumn column) {
        PortalEntityField entityField = field.getAnnotation(PortalEntityField.class);
        if (FuncUtil.isNotEmpty(entityField)) {
            if (FuncUtil.isNotEmpty(entityField.joinField())) {
                column.setFieldType(PortalFieldDict.ENTITY.getValue());
                column.setReference(entityField.entity().getSimpleName());
                column.setDbField(entityField.joinField());
                column.setEntityField(entityField.field());
                column.setEditShow(CommonConst.YES);
                column.setAddShow(CommonConst.YES);
            } else {
                column.setEditShow(CommonConst.NO);
                column.setAddShow(CommonConst.NO);
            }
            if (entityField.aggregation()) {
                if (StringUtil.convertSwitch(portal.getAdvanced())) {
                    portal.setAdvanced(StringUtil.convertSwitch(false));
                    sysPortalService.updateById(portal);
                }
            }
        }
    }

    private void handlePortalNoFilterField(Field field, SysPortalColumn column) {
        PortalNoFilterField portalNoFilterField = field.getAnnotation(PortalNoFilterField.class);
        if (FuncUtil.isNotEmpty(portalNoFilterField)) {
            column.setFilterAble(CommonConst.NO);
        } else {
            column.setFilterAble(CommonConst.YES);
        }
    }

    private void handlePortalSortField(Field field, SysPortalColumn column) {
        PortalSortField portalSortField = field.getAnnotation(PortalSortField.class);
        if (FuncUtil.isNotEmpty(portalSortField)) {
            column.setSortAble(CommonConst.YES);
        } else {
            column.setSortAble(CommonConst.NO);
        }
    }

    private void handlePortalDisplayOnlyField(Field field, SysPortalColumn column) {
        PortalDisplayOnlyField portalDisplayOnlyField = field.getAnnotation(PortalDisplayOnlyField.class);
        if (FuncUtil.isNotEmpty(portalDisplayOnlyField)) {
            column.setShow(portalDisplayOnlyField.table());
            column.setDetailShow(portalDisplayOnlyField.detail());
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
        }
    }

    private void handlePortalDisplayNoneField(Field field, SysPortalColumn column) {
        PortalDisplayNoneField portalDisplayNoneField = field.getAnnotation(PortalDisplayNoneField.class);
        if (FuncUtil.isNotEmpty(portalDisplayNoneField)) {
            column.setShow(CommonConst.NO);
            column.setDetailShow(CommonConst.NO);
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
        }
    }

    private void handleGroupDisplayField(Field field, SysPortalColumn column) {
        PortalGroupDisplayField portalGroupDisplayField = field.getAnnotation(PortalGroupDisplayField.class);
        if (FuncUtil.isNotEmpty(portalGroupDisplayField)) {
            column.setDisplayGroupName(portalGroupDisplayField.value());
        }
    }

    private void handleConvertField(Field field, SysPortalColumn column) {
        Convert convertAnno = field.getAnnotation(Convert.class);
        if (FuncUtil.isNotEmpty(convertAnno)) {
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
            column.setFilterAble(CommonConst.NO);
            column.setSortAble(CommonConst.NO);
        }
    }

    private void handleBindField(Field field, SysPortalColumn column) {
        BindField bindFieldAnno = field.getAnnotation(BindField.class);
        if (FuncUtil.isNotEmpty(bindFieldAnno)) {
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
            column.setFilterAble(CommonConst.NO);
            column.setSortAble(CommonConst.NO);
        }
        BindFieldList bindFieldList = field.getAnnotation(BindFieldList.class);
        if (FuncUtil.isNotEmpty(bindFieldList)) {
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
            column.setFilterAble(CommonConst.NO);
            column.setSortAble(CommonConst.NO);
        }
        BindEntity bindEntityAnno = field.getAnnotation(BindEntity.class);
        if (FuncUtil.isNotEmpty(bindEntityAnno)) {
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
            column.setFilterAble(CommonConst.NO);
            column.setSortAble(CommonConst.NO);
        }
        BindEntityList bindFieldListAnno = field.getAnnotation(BindEntityList.class);
        if (FuncUtil.isNotEmpty(bindFieldListAnno)) {
            column.setAddShow(CommonConst.NO);
            column.setEditShow(CommonConst.NO);
            column.setFilterAble(CommonConst.NO);
            column.setSortAble(CommonConst.NO);
        }
    }

    private void handlePortalDictField(Field field, SysPortalColumn column) {
        PortalDictField portalDictField = field.getAnnotation(PortalDictField.class);
        if (FuncUtil.isNotEmpty(portalDictField)) {
            MetaDict metaDict = portalDictField.value().getAnnotation(MetaDict.class);
            if (FuncUtil.isNotEmpty(metaDict)) {
                column.setFieldType(PortalFieldDict.ENUM.getValue());
                column.setReference(metaDict.value());
            }
            MetaTreeDict metaTreeDict = portalDictField.value().getAnnotation(MetaTreeDict.class);
            if (FuncUtil.isNotEmpty(metaTreeDict)) {
                column.setFieldType(PortalFieldDict.TREE.getValue());
                column.setReference(metaTreeDict.value());
            }
        }
    }

    private void handlePortalMoneyField(Field field, SysPortalColumn column) {
        PortalMoneyField portalMoneyField = field.getAnnotation(PortalMoneyField.class);
        if (FuncUtil.isNotEmpty(portalMoneyField)) {
            column.setFieldType(PortalFieldDict.MONEY.getValue());
            column.setReference(StringUtil.joinWith(",", Integer.valueOf(portalMoneyField.fix()).toString(),
                    Integer.valueOf(portalMoneyField.unit()).toString()));
            column.setSortAble(CommonConst.YES);
            column.setSummaryAble(CommonConst.YES);
        }
    }

    private void handlePortalPercentField(Field field, SysPortalColumn column) {
        PortalPercentField portalPercentField = field.getAnnotation(PortalPercentField.class);
        if (FuncUtil.isNotEmpty(portalPercentField)) {
            column.setFieldType(PortalFieldDict.PERCENT.getValue());
            column.setReference(StringUtil.joinWith(",", Integer.valueOf(portalPercentField.fix()).toString(),
                    Integer.valueOf(portalPercentField.unit()).toString()));
            column.setSortAble(CommonConst.YES);
        }
    }

    private void handlePortalImageField(Field field, SysPortalColumn column) {
        PortalImageField portalImageField = field.getAnnotation(PortalImageField.class);
        if (FuncUtil.isNotEmpty(portalImageField)) {
            column.setFieldType(PortalFieldDict.IMAGE.getValue());
            column.setFilterAble(CommonConst.NO);
        }
    }

    private void handlePortalAudioField(Field field, SysPortalColumn column) {
        PortalAudioField portalAudioField = field.getAnnotation(PortalAudioField.class);
        if (FuncUtil.isNotEmpty(portalAudioField)) {
            column.setFieldType(PortalFieldDict.AUDIO.getValue());
            column.setFilterAble(CommonConst.NO);
        }
    }

    private void handlePortalTextAreaField(Field field, SysPortalColumn column) {
        PortalTextAreaField portalTextAreaField = field.getAnnotation(PortalTextAreaField.class);
        if (FuncUtil.isNotEmpty(portalTextAreaField)) {
            column.setFieldType(PortalFieldDict.TEXT.getValue());
            column.setFilterAble(CommonConst.NO);
        }
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

    @Transactional(rollbackFor = Exception.class)
    public void importConfig(InputStream inputStream, PortalReq req) {
        PortalWithColumnsRes portalWithColumns = JsonUtil.readStreamJson(inputStream, PortalWithColumnsRes.class);
        Validator.assertNotNull(portalWithColumns, ErrCodeSys.SYS_ERR_MSG, "配置解析失败");
        Validator.assertNotEmpty(portalWithColumns.getColumns(), ErrCodeSys.PA_DATA_NOT_EXIST, "字段配置信息");
        SysPortal portal;
        if (FuncUtil.isEmpty(req.getName())) {
            portal = sysPortalService.getByName(portalWithColumns.getName(), portalWithColumns.getRoleId());
        } else {
            portal = sysPortalService.getByName(req.getName(), req.getRoleId());
        }
        if (FuncUtil.isNotEmpty(portal)) {
            updatePortal(portalWithColumns, portal);
            updateColumnConfig(portalWithColumns, portal);
            updateAssociateConfig(portalWithColumns, portal);
        }
    }

    private void updatePortal(PortalWithColumnsRes portalWithColumns, SysPortal portal) {
        portalWithColumns.setId(portal.getId());
        portalWithColumns.setRoleId(portal.getRoleId());
        if (!FuncUtil.equals(portal.getName(), portalWithColumns.getName())) {
            portalWithColumns.setName(portal.getName());
            portalWithColumns.setDisplayName(portal.getDisplayName());
        }
        portalWithColumns.setReferenceId(portal.getReferenceId());
        portalWithColumns.setUrl(portal.getUrl());
        portalWithColumns.setBean(portal.getBean());
        sysPortalService.updateById(portalWithColumns);
    }

    private void updateColumnConfig(PortalWithColumnsRes portalWithColumns, SysPortal portal) {
        List<SysPortalColumn> columns = sysPortalColumnService.getPropertyListByPortalId(portal.getId(),
                portal.getRoleId());
        Map<String, SysPortalColumn> map = ReflectionUtil.reflectToMap(columns, "portalId", "roleId", "property");
        List<SysPortalColumn> newColumns = new ArrayList<>();
        if (FuncUtil.isNotEmpty(portalWithColumns.getColumns())) {
            for (SysPortalColumn column : portalWithColumns.getColumns()) {
                SysPortalColumn sysPortalColumn = map.get(
                        StringUtil.join(portal.getId().toString(), column.getRoleId().toString(),
                                column.getProperty()));
                if (FuncUtil.isNotEmpty(sysPortalColumn)) {
                    column.setRoleId(portal.getRoleId());
                    column.setPortalId(portal.getId());
                    column.setId(sysPortalColumn.getId());
                    newColumns.add(column);
                }
            }
            if (FuncUtil.isNotEmpty(newColumns)) {
                sysPortalColumnService.updateById(newColumns);
            }
        }
    }

    private void updateAssociateConfig(PortalWithColumnsRes portalWithColumns, SysPortal portal) {
        List<SysPortalAssociate> associates = sysPortalAssociateService.getPropertyListByPortalId(portal.getId(),
                portal.getRoleId());
        Map<String, SysPortalAssociate> map = ReflectionUtil.reflectToMap(associates, "portalId", "roleId", "title");
        List<SysPortalAssociate> resList = ReflectionUtil.copyList(portalWithColumns.getAssociates(),
                SysPortalAssociate.class);
        List<SysPortalAssociate> newAssociate = new ArrayList<>();
        if (FuncUtil.isNotEmpty(portalWithColumns.getColumns())) {
            for (SysPortalAssociate associate : resList) {
                SysPortalAssociate sysPortalAssociate = map.get(
                        StringUtil.join(portal.getId().toString(), associate.getRoleId().toString(),
                                associate.getTitle()));
                if (FuncUtil.isNotEmpty(sysPortalAssociate)) {
                    sysPortalAssociate.setRoleId(portal.getRoleId());
                    sysPortalAssociate.setPortalId(portal.getId());
                    sysPortalAssociate.setId(sysPortalAssociate.getId());
                    newAssociate.add(sysPortalAssociate);
                }
            }
            if (FuncUtil.isNotEmpty(newAssociate)) {
                sysPortalAssociateService.updateById(newAssociate);
            }
        }
    }

    public String getPortalSql(String name) {
        SysPortal sysPortal = sysPortalService.getByName(name, DEFAULT_CONFIG_ROLE_ID);
        AdminControllerInf<?, ?> bean = (AdminControllerInf<?, ?>) BeanUtil.getBean(sysPortal.getBean());
        Validator.assertNotNull(bean, ErrCodeSys.PA_DATA_NOT_EXIST, name);
        return bean.getPortalSql();
    }
}
