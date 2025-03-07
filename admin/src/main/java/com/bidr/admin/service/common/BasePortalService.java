package com.bidr.admin.service.common;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.config.PortalEntityField;
import com.bidr.admin.config.PortalNoFilterField;
import com.bidr.admin.config.PortalSelect;
import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.admin.service.excel.handler.PortalExcelInsertHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelParseHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelTemplateHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelUpdateHandlerInf;
import com.bidr.admin.service.excel.listener.PortalExcelInsertListener;
import com.bidr.admin.service.excel.listener.PortalExcelUpdateListener;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.common.convert.Convert;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.platform.bo.excel.ExcelExportBO;
import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import com.diboot.core.binding.annotation.BindField;
import com.github.yulichang.toolkit.support.ColumnCache;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectCache;
import com.github.yulichang.wrapper.segments.SelectString;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

import static com.bidr.kernel.constant.db.SqlConstant.VALID_FIELD;

/**
 * Title: BasePortalService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/25 15:12
 */
@Slf4j
@SuppressWarnings("rawtypes, unchecked")
public abstract class BasePortalService<ENTITY, VO> implements PortalCommonService<ENTITY, VO>, CommandLineRunner, PortalExcelUploadProgressInf, PortalExcelInsertHandlerInf<ENTITY>, PortalExcelUpdateHandlerInf<ENTITY>, PortalExcelParseHandlerInf<ENTITY, VO>, PortalExcelTemplateHandlerInf {

    protected Map<String, String> aliasMap = new HashMap<>(32);
    protected Map<String, String> summaryAliasMap = new HashMap<>(32);
    protected Set<String> havingFields = new HashSet<>();
    protected Map<String, String> selectApplyMap = new HashMap<>(32);
    @Resource
    protected SysPortalService sysPortalService;
    @Resource
    protected DictCacheService dictCacheService;
    @Resource
    protected TokenService tokenService;
    @Resource
    protected ApplicationContext applicationContext;
    @Resource
    protected SysConfigCacheService sysConfigCacheService;

    @Override
    public void run(String... args) {
        for (Field field : ReflectionUtil.getFields(getVoClass())) {
            setAlias(field, aliasMap);
            setSummaryAlias(field, summaryAliasMap);
            setHavingField(field, havingFields);
            setApplyField(field, selectApplyMap);
        }
    }

    protected void setAlias(Field field, Map<String, String> map) {
        PortalNoFilterField portalNoFilterField = field.getAnnotation(PortalNoFilterField.class);
        if (FuncUtil.isNotEmpty(portalNoFilterField)) {
            return;
        }
        PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
        if (FuncUtil.isNotEmpty(portalEntityField)) {
            if (FuncUtil.isNotEmpty(portalEntityField.alias())) {
                map.put(field.getName(),
                        getAlias(portalEntityField.entity(), portalEntityField.field(), portalEntityField.alias()));
            } else if (!FuncUtil.equals(portalEntityField.entity(), Object.class)) {
                map.put(field.getName(), getAlias(portalEntityField.entity(), portalEntityField.field()));
            } else {
                map.put(field.getName(), portalEntityField.field());
            }
        } else {
            BindField bindField = field.getAnnotation(BindField.class);
            if (FuncUtil.isNotEmpty(bindField)) {
                map.put(field.getName(), getAlias(bindField.entity(), bindField.field()));
            }
        }
    }

    protected void setSummaryAlias(Field field, Map<String, String> map) {
        PortalNoFilterField portalNoFilterField = field.getAnnotation(PortalNoFilterField.class);
        if (FuncUtil.isNotEmpty(portalNoFilterField)) {
            return;
        }
        PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
        if (FuncUtil.isNotEmpty(portalEntityField)) {
            if (FuncUtil.isNotEmpty(portalEntityField.origFieldName())) {
                map.put(field.getName(), portalEntityField.origFieldName());
            } else {
                if (FuncUtil.isNotEmpty(portalEntityField.alias())) {
                    map.put(field.getName(),
                            getAlias(portalEntityField.entity(), portalEntityField.field(), portalEntityField.alias()));
                } else if (!FuncUtil.equals(portalEntityField.entity(), Object.class)) {
                    map.put(field.getName(), getAlias(portalEntityField.entity(), portalEntityField.field()));
                } else {
                    map.put(field.getName(), portalEntityField.field());
                }
            }
        } else {
            BindField bindField = field.getAnnotation(BindField.class);
            if (FuncUtil.isNotEmpty(bindField)) {
                map.put(field.getName(), getAlias(bindField.entity(), bindField.field()));
            }
        }
    }

    protected void setHavingField(Field field, Set<String> set) {
        PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
        if (FuncUtil.isNotEmpty(portalEntityField)) {
            if (portalEntityField.aggregation()) {
                set.add(field.getName());
            }
        }
    }

    private void setApplyField(Field field, Map<String, String> map) {
        PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
        if (FuncUtil.isNotEmpty(portalEntityField)) {
            if (portalEntityField.selectApply()) {
                map.put(field.getName(), portalEntityField.field());
            }
        }
    }

    private String getAlias(Class<?> clazz, String fieldName, String alias) {
        String selectSqlName = DbUtil.getSelectSqlName(clazz, fieldName);
        return alias + "." + selectSqlName;
    }

    @Override
    public final MPJLambdaWrapper<ENTITY> getJoinWrapper() {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        getJoinWrapper(wrapper);
        return wrapper;
    }

    /**
     * 根据vo
     * 生成生成联表查询wrapper
     * select as 其他表字段
     *
     * @return 联表wrapper
     */
    @Override
    public void getJoinWrapper(MPJLambdaWrapper<ENTITY> wrapper) {
        Map<String, String> selectColumnMap = new LinkedHashMap<>();
        List<String> groupColumns = new ArrayList<>();
        List<String> unSelectFields = new ArrayList<>();
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            wrapper.eq(wrapper.getAlias() + "." + VALID_FIELD, CommonConst.YES);
        }
        for (Field field : ReflectionUtil.getFieldMap(getVoClass()).values()) {
            boolean selected = false;
            selected |= parsePortalEntityField(wrapper, field);
            selected |= parsePortalSelect(selectColumnMap, groupColumns, field);
            if (!selected) {
                unSelectFields.add(field.getName());
            }
        }
        if (FuncUtil.isNotEmpty(selectColumnMap)) {
            for (Map.Entry<String, String> entry : selectColumnMap.entrySet()) {
                wrapper.getSelectColum()
                        .add(new SelectString(StringUtil.joinWith(" as ", entry.getKey(), "'" + entry.getValue() + "'"),
                                wrapper.getAlias()));
            }
        } else {
            if (FuncUtil.isNotEmpty(unSelectFields)) {
                Map<String, Field> fieldMap = ReflectionUtil.getFieldMap(getEntityClass());
                for (String unselectedField : unSelectFields) {
                    Field field = fieldMap.get(unselectedField);
                    if (FuncUtil.isNotEmpty(field) && !getSelectApplyMap().containsKey(field.getName())) {
                        String sqlFieldName = getRepo().getColumnName(field.getName(), getAliasMap(), getEntityClass());
                        wrapper.getSelectColum().add(new SelectString(
                                StringUtil.joinWith(" as ", sqlFieldName, "'" + field.getName() + "'"),
                                wrapper.getAlias()));
                    }
                }
            }
        }
        if (FuncUtil.isNotEmpty(groupColumns)) {
            for (String groupColumn : groupColumns) {
                wrapper.groupBy(groupColumn);
            }
        }
    }


    private String getAlias(Class<?> clazz, String fieldName) {
        String tableName = DbUtil.getTableName(clazz);
        String selectSqlName = DbUtil.getSelectSqlName(clazz, fieldName);
        return tableName + "." + selectSqlName;
    }

    protected void addAliasMap(GetFunc reqFiled, String alias, GetFunc entityField) {
        aliasMap.put(LambdaUtil.getFieldNameByGetFunc(reqFiled), alias + "." + DbUtil.getSelectSqlName(entityField));
    }

    protected void addAliasMap(GetFunc<VO, ?> field) {
        aliasMap.put(LambdaUtil.getFieldNameByGetFunc(field), LambdaUtil.getFieldNameByGetFunc(field));
    }

    private boolean parsePortalSelect(Map<String, String> selectColumnMap, List<String> groupColumns, Field field) {
        PortalSelect portalSelect = field.getAnnotation(PortalSelect.class);
        if (FuncUtil.isNotEmpty(portalSelect)) {
            if (portalSelect.value()) {
                String sqlFieldName = getRepo().getColumnName(field.getName(), getAliasMap(), getEntityClass());
                selectColumnMap.put(sqlFieldName, field.getName());
                if (portalSelect.group()) {
                    groupColumns.add(sqlFieldName);
                }
            }
            return true;
        } else {
            return FuncUtil.isNotEmpty(field.getAnnotation(Convert.class));
        }
    }

    @Override
    public void prepareInsert(ENTITY entity) {
        if (isAdmin()) {
            adminBeforeAdd(entity);
        } else {
            beforeAdd(entity);
        }
    }

    @Override
    public boolean validateInsert(ENTITY entity, List<ENTITY> cachedList, Map<Object, Object> validateMap) {
        return true;
    }

    @Override
    public void handleInsert(List<ENTITY> entityList) {
        batchInsert(entityList);
    }

    private boolean parsePortalEntityField(MPJLambdaWrapper<ENTITY> wrapper, Field field) {
        PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
        if (FuncUtil.isNotEmpty(portalEntityField) && !getSelectApplyMap().containsKey(field.getName())) {
            String alias = portalEntityField.alias();
            String sqlFieldName = portalEntityField.field();
            if (!FuncUtil.equals(portalEntityField.entity(), Object.class)) {
                if (FuncUtil.isEmpty(alias)) {
                    alias = DbUtil.getTableName(portalEntityField.entity());
                }
                Map<String, SelectCache> cacheMap = ColumnCache.getMapField(portalEntityField.entity());
                SelectCache cache = cacheMap.get(portalEntityField.field());
                sqlFieldName = StringUtil.joinWith(".", alias, cache.getColumn());
                wrapper.getSelectColum()
                        .add(new SelectString(sqlFieldName + " AS " + field.getName(), wrapper.getAlias()));
            } else {
                wrapper.getSelectColum()
                        .add(new SelectString(sqlFieldName + " AS " + field.getName(), wrapper.getAlias()));
            }
            if (portalEntityField.group()) {
                wrapper.groupBy(field.getName());
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void afterInsert(List<ENTITY> entityList) {
        for (ENTITY entity : entityList) {
            afterAdd(entity);
        }
    }

    @Override
    public void prepareUpdate(ENTITY entity) {
        if (isAdmin()) {
            adminBeforeUpdate(entity);
        } else {
            beforeUpdate(entity);
        }
    }

    @Override
    public boolean validateUpdate(ENTITY entity, List<ENTITY> cachedList, Map<Object, Object> validateMap) {
        return true;
    }

    @Override
    public void handleUpdate(List<ENTITY> entityList) {
        batchUpdate(entityList);
    }

    @Override
    public void afterUpdate(List<ENTITY> entityList) {
        for (ENTITY entity : entityList) {
            afterUpdate(entity);
        }
    }

    @Override
    public TokenService getTokenService() {
        return tokenService;
    }

    @Override
    public String getProgressKey() {
        return "UPLOAD_PROGRESS_" + getEntityClass().getSimpleName();
    }


    @Override
    public Map<String, String> getAliasMap() {
        return aliasMap;
    }

    @Override
    public Map<String, String> getSummaryAliasMap() {
        return summaryAliasMap;
    }

    @Override
    public Set<String> getHavingFields() {
        return havingFields;
    }

    @Override
    public Map<String, String> getSelectApplyMap() {
        return selectApplyMap;
    }


    @SneakyThrows
    @Override
    public byte[] export(List<VO> dataList, String portalName) {
        PortalWithColumnsRes portal = sysPortalService.getExportPortal(portalName,
                PortalConfigContext.getPortalConfigRoleId());
        ExcelExportBO bo = new ExcelExportBO();
        bo.setTitle(portal.getDisplayName());
        if (FuncUtil.isNotEmpty(dataList)) {
            for (VO data : dataList) {
                Map<String, Object> hashMap = ReflectionUtil.getHashMap(data);
                List<String> records = new ArrayList<>();
                for (SysPortalColumn column : portal.getColumns()) {
                    Object value = hashMap.get(column.getProperty());
                    String result = StringUtil.parse(value);
                    String reference = column.getReference();
                    try {
                        switch (DictEnumUtil.getEnumByValue(column.getFieldType(), PortalFieldDict.class)) {
                            case ENUM:
                                String[] split = result.split(",");
                                List<String> dictResult = new ArrayList<>();
                                for (String s : split) {
                                    try {
                                        SysDict dictByValue = dictCacheService.getDictByValue(reference, s);
                                        if (FuncUtil.isNotEmpty(dictByValue)) {
                                            dictResult.add(dictByValue.getDictLabel());
                                        }
                                    } catch (Exception e) {
                                        log.error("", e);
                                    }
                                }
                                result = StringUtil.join(dictResult.toArray(new String[0]));
                                break;
                            case DATE:
                                result = StringUtil.parse(value, DateUtil.DATE_NORMAL);
                                break;
                            default:
                                break;
                        }
                        records.add(result);
                        bo.getColumnTitles().add(column.getDisplayName());
                    } catch (Exception e) {
                        log.error("", e);
                    }

                }
                bo.getRecords().add(records);
            }
        } else {
            for (SysPortalColumn column : portal.getColumns()) {
                bo.getColumnTitles().add(column.getDisplayName());
            }
        }
        return export(bo);
    }


    @Override
    public byte[] templateExport(String portalName) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PortalWithColumnsRes portal = sysPortalService.getImportPortal(portalName,
                PortalConfigContext.getPortalConfigRoleId());
        templateExcel(os, portal, getVoClass());
        return os.toByteArray();
    }


    @Override
    public BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo() {
        return (BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY>) applicationContext.getBean(
                StrUtil.lowerFirst(getEntityClass().getSimpleName()) + "Service");
    }


    @Async
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readExcelForInsert(InputStream is, String portalName) {
        validateReadExcel();
        try {
            startUploadProgress(0);
            PortalWithColumnsRes portal = sysPortalService.getImportPortal(portalName,
                    PortalConfigContext.getPortalConfigRoleId());
            handleExcelInsert(is, portal);
        } catch (Exception e) {
            log.error("读取excel插入数据失败", e);
            uploadProgressException(e.getMessage());
            throw e;
        }
    }


    @Async
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void readExcelForUpdate(InputStream is, String portalName) {
        try {
            startUploadProgress(0);
            PortalWithColumnsRes portal = sysPortalService.getImportPortal(portalName,
                    PortalConfigContext.getPortalConfigRoleId());
            handleExcelUpdate(is, portal);
        } catch (Exception e) {
            log.error("读取excel更新数据失败", e);
            uploadProgressException(e.getMessage());
            throw e;
        }

    }

    //todo 尚未验证
    protected void handleExcelUpdate(InputStream is, PortalWithColumnsRes portal) {
        EasyExcel.read(is).sheet().registerReadListener(
                        new PortalExcelUpdateListener<Map<Integer, String>>(portal, this,
                                (PortalExcelParseHandlerInf<ENTITY, Map<Integer, String>>) this::parseCommonEntity, this))
                .head(buildExcelHead(portal, getVoClass())).doRead();
    }

    @Override
    public void validateReadExcel() {
        PortalUploadProgressRes uploadProgress = getUploadProgress();
        if (FuncUtil.isNotEmpty(uploadProgress)) {
            Validator.assertTrue(FuncUtil.notEquals(uploadProgress.getStep(), UploadProgressStep.VALIDATE),
                    ErrCodeSys.SYS_ERR_MSG, "当前已有上传任务正在执行");
            Validator.assertTrue(FuncUtil.notEquals(uploadProgress.getStep(), UploadProgressStep.SAVE),
                    ErrCodeSys.SYS_ERR_MSG, "当前已有上传任务正在执行");
        }
    }

    @Override
    public Object getUploadProgressRes(String portal) {
        return getUploadProgress();
    }

    protected void handleExcelInsert(InputStream is, PortalWithColumnsRes portal) {
        EasyExcel.read(is, getVoClass(), new PortalExcelInsertListener(portal, this, this, this, 3000)).sheet()
                .headRowNumber(1).doRead();
    }

    @Override
    public ENTITY parseEntity(PortalWithColumnsRes portal, VO data, Map<String, Map<String, Object>> entityCache) {
        return ReflectionUtil.copy(data, getEntityClass());
    }

    protected ENTITY parseCommonEntity(PortalWithColumnsRes portal, Map<Integer, String> data,
                                       Map<String, Map<String, Object>> entityCache) {
        ENTITY entity = ReflectionUtil.newInstance(getEntityClass());
        for (int columnIndex = 0; columnIndex < portal.getColumns().size(); columnIndex++) {
            SysPortalColumn column = portal.getColumns().get(columnIndex);
            String value = data.get(columnIndex);
            if (FuncUtil.isNotEmpty(value)) {
                Class<?> clazz = null;
                Object result;
                String field = column.getProperty();
                try {
                    switch (DictEnumUtil.getEnumByValue(column.getFieldType(), PortalFieldDict.class)) {
                        case ENUM:
                            clazz = ReflectionUtil.getField(entity, field).getType();
                            result = dictCacheService.getDictByLabel(column.getReference(), value).getDictValue();
                            break;
                        case ENTITY:
                            field = column.getDbField();
                            clazz = ReflectionUtil.getField(entity, field).getType();
                            result = parseReferenceEntity(column, entityCache, value);
                            break;
                        case DATETIME:
                            result = DateUtil.formatDate(value, DateUtil.DATE_TIME_NORMAL);
                            break;
                        case DATE:
                            result = DateUtil.formatDate(value, DateUtil.DATE);
                            break;
                        default:
                            clazz = ReflectionUtil.getField(entity, field).getType();
                            result = value;
                            break;
                    }
                    if (FuncUtil.isNotEmpty(clazz)) {
                        result = JsonUtil.readJson(result, clazz);
                    }
                    ReflectionUtil.setValue(entity, field, result);
                } catch (Exception e) {
                    log.error("", e);
                }
            } else {
                Validator.assertFalse(StringUtil.convertSwitch(column.getRequired()), ErrCodeSys.SYS_ERR_MSG,
                        "字段[" + column.getDisplayName() + "]必填");
            }

        }
        return entity;
    }

    @SneakyThrows
    protected Object parseReferenceEntity(SysPortalColumn column, Map<String, Map<String, Object>> entityCache,
                                          String entityName) {
        SysPortal entityPortal = sysPortalService.getByName(column.getReference(),
                PortalConfigContext.getPortalConfigRoleId());
        if (FuncUtil.isEmpty(entityCache.get(column.getReference()))) {
            entityCache.put(column.getReference(), new HashMap(16));
        }
        Object result = entityCache.get(column.getReference()).get(entityName);
        if (FuncUtil.isEmpty(result)) {
            AdminControllerInf bean = (AdminControllerInf) BeanUtil.getBean(Class.forName(entityPortal.getBean()));
            Validator.assertNotNull(bean, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
            AdvancedQuery entityCondition = JsonUtil.readJson(column.getEntityCondition(), AdvancedQuery.class);
            AdvancedQueryReq req = new AdvancedQueryReq();
            req.setCondition(entityCondition);
            AdvancedQuery query = new AdvancedQuery(entityPortal.getNameColumn(), entityName);
            mergeQuery(req, query);
            Page page = bean.advancedQuery(req);
            Validator.assertTrue(page.getRecords().size() == 1, ErrCodeSys.SYS_ERR_MSG, "匹配实体数据失败");
            result = ReflectionUtil.getValue(page.getRecords().get(0), column.getEntityField(), Object.class);
            entityCache.get(column.getReference()).put(entityName, result);
        }
        return result;
    }

    protected void mergeQuery(AdvancedQueryReq req, AdvancedQuery condition) {
        AdvancedQuery mergedQuery = new AdvancedQuery();
        mergedQuery.setAndOr(SqlConstant.AND);
        mergedQuery.getConditionList().add(condition);
        if (FuncUtil.isNotEmpty(req.getCondition())) {
            mergedQuery.getConditionList().add(req.getCondition());
        }
        req.setCondition(mergedQuery);
    }


}
