package com.bidr.admin.service.common;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.config.PortalEntityField;
import com.bidr.admin.config.PortalNoFilterField;
import com.bidr.admin.constant.dict.UploadProgressStep;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.service.excel.handler.PortalExcelInsertHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelParseHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelTemplateHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelUpdateHandlerInf;
import com.bidr.admin.service.excel.listener.PortalExcelInsertListener;
import com.bidr.admin.service.excel.listener.PortalExcelUpdateListener;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.admin.vo.PortalUploadProgressRes;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.constant.dict.portal.PortalFieldDict;
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
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.diboot.core.binding.annotation.BindField;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: BasePortalService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/25 15:12
 */
@Slf4j
@SuppressWarnings("rawtypes, unchecked")
public abstract class BasePortalService<ENTITY, VO> implements PortalCommonService<ENTITY, VO>, CommandLineRunner,
        PortalExcelUploadProgressInf, PortalExcelInsertHandlerInf<ENTITY>, PortalExcelUpdateHandlerInf<ENTITY>,
        PortalExcelTemplateHandlerInf {

    protected Map<String, String> aliasMap = new HashMap<>(32);
    @Resource
    protected SysPortalService sysPortalService;
    @Resource
    protected DictCacheService dictCacheService;
    @Resource
    protected TokenService tokenService;
    @Resource
    protected DataSourceTransactionManager dataSourceTransactionManager;
    @Resource
    protected TransactionDefinition transactionDefinition;
    @Resource
    protected ApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        for (Field field : ReflectionUtil.getFields(getVoClass())) {
            PortalNoFilterField portalNoFilterField = field.getAnnotation(PortalNoFilterField.class);
            if (FuncUtil.isNotEmpty(portalNoFilterField)) {
                continue;
            }
            PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
            if (FuncUtil.isNotEmpty(portalEntityField)) {
                if (FuncUtil.isNotEmpty(portalEntityField.alias())) {
                    aliasMap.put(field.getName(),
                            getAlias(portalEntityField.entity(), portalEntityField.field(), portalEntityField.alias()));
                } else {
                    aliasMap.put(field.getName(), getAlias(portalEntityField.entity(), portalEntityField.field()));
                }
                continue;
            }
            BindField bindField = field.getAnnotation(BindField.class);
            if (FuncUtil.isNotEmpty(bindField)) {
                aliasMap.put(field.getName(), getAlias(bindField.entity(), bindField.field()));
                continue;
            }
        }
    }

    private String getAlias(Class<?> clazz, String fieldName, String alias) {
        String selectSqlName = DbUtil.getSelectSqlName(clazz, fieldName);
        return alias + "." + selectSqlName;
    }

    private String getAlias(Class<?> clazz, String fieldName) {
        String tableName = DbUtil.getTableName(clazz);
        String selectSqlName = DbUtil.getSelectSqlName(clazz, fieldName);
        return tableName + "." + selectSqlName;
    }

    protected void addAliasMap(GetFunc reqFiled, String alias, GetFunc entityField) {
        aliasMap.put(LambdaUtil.getFieldNameByGetFunc(reqFiled), alias + "." + DbUtil.getSelectSqlName(entityField));
    }

    @Override
    public Map<String, String> getAliasMap() {
        return aliasMap;
    }

    @SneakyThrows
    @Override
    public byte[] export(List<VO> dataList, String portalName) {
        PortalWithColumnsRes portal = sysPortalService.getExportPortal(portalName);
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
                                result = dictCacheService.getDictByValue(reference, result).getDictLabel();
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
        PortalWithColumnsRes portal = sysPortalService.getImportPortal(portalName);
        templateExcel(os, portal);
        return os.toByteArray();
    }

    @Override
    public BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo() {
        return (BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY>) applicationContext.getBean(
                StrUtil.lowerFirst(getEntityClass().getSimpleName()) + "Service");
    }

    @Async
    @Override
    public void readExcelForInsert(InputStream is, String portalName) {
        validateReadExcel();
        TransactionStatus transactionStatus = dataSourceTransactionManager.getTransaction(transactionDefinition);
        try {
            startUploadProgress(0);
            PortalWithColumnsRes portal = sysPortalService.getImportPortal(portalName);
            handleExcelInsert(is, portal);
            dataSourceTransactionManager.commit(transactionStatus);
        } catch (Exception e) {
            log.error("读取excel插入数据失败", e);
            uploadProgressException(e.getMessage());
            dataSourceTransactionManager.rollback(transactionStatus);

        }
    }

    @Async
    @Override
    public void readExcelForUpdate(InputStream is, String portalName) {
        validateReadExcel();
        try {
            startUploadProgress(0);
            PortalWithColumnsRes portal = sysPortalService.getImportPortal(portalName);
            handleExcelUpdate(is, portal);
        } catch (Exception e) {
            log.error("读取excel更新数据失败", e);
            uploadProgressException(e.getMessage());
        }

    }

    protected void handleExcelUpdate(InputStream is, PortalWithColumnsRes portal) {
        EasyExcel.read(is).sheet().registerReadListener(
                        new PortalExcelUpdateListener<Map<Integer, String>>(portal, this,
                                (PortalExcelParseHandlerInf<ENTITY, Map<Integer, String>>) this::parseEntity, this))
                .head(buildExcelHead(portal)).doRead();
    }

    @Override
    public Object getUploadProgressRes(String portal) {
        return getUploadProgress();
    }

    private void validateReadExcel() {
        PortalUploadProgressRes uploadProgress = getUploadProgress();
        if (FuncUtil.isNotEmpty(uploadProgress)) {
            Validator.assertTrue(FuncUtil.notEquals(uploadProgress.getStep(), UploadProgressStep.VALIDATE),
                    ErrCodeSys.SYS_ERR_MSG, "当前已有上传任务正在执行");
            Validator.assertTrue(FuncUtil.notEquals(uploadProgress.getStep(), UploadProgressStep.SAVE),
                    ErrCodeSys.SYS_ERR_MSG, "当前已有上传任务正在执行");
        }
    }

    protected void handleExcelInsert(InputStream is, PortalWithColumnsRes portal) {
        EasyExcel.read(is).sheet().registerReadListener(new PortalExcelInsertListener(portal, this,
                        (PortalExcelParseHandlerInf<ENTITY, Map<Integer, String>>) this::parseEntity, this))
                .head(buildExcelHead(portal)).doRead();
    }

    public ENTITY parseEntity(PortalWithColumnsRes portal, Map<Integer, String> data,
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
        SysPortal entityPortal = sysPortalService.getByName(column.getReference());
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

    @Override
    public void prepareInsert(ENTITY entity) {
        if (isAdmin()) {
            adminBeforeAdd(entity);
        } else {
            beforeAdd(entity);
        }
    }

    @Override
    public void validateInsert(ENTITY entity, List<ENTITY> cachedList, Map<Object, Object> validateMap) {

    }

    @Override
    public void handleInsert(List<ENTITY> entityList) {
        batchInsert(entityList);
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
    public void validateUpdate(ENTITY entity, List<ENTITY> cachedList, Map<Object, Object> validateMap) {

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
}
