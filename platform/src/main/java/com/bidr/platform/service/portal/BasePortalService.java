package com.bidr.platform.service.portal;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.anno.PortalEntityField;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.bo.excel.ExcelExportBO;
import com.bidr.platform.config.portal.PortalEntity;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.bidr.platform.utils.excel.JxlsUtil;
import com.bidr.platform.vo.portal.PortalWithColumnsRes;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.Resource;
import java.io.*;
import java.lang.reflect.Field;
import java.text.ParseException;
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
public abstract class BasePortalService<ENTITY, VO> implements PortalCommonService<ENTITY, VO>, CommandLineRunner {

    protected Map<String, String> aliasMap = new HashMap<>(32);
    @Resource
    private PortalService portalService;

    @Override
    public void run(String... args) {
        for (Field field : ReflectionUtil.getFields(getVoClass())) {
            PortalEntityField portalEntityField = field.getAnnotation(PortalEntityField.class);
            if (FuncUtil.isNotEmpty(portalEntityField)) {
                aliasMap.put(field.getName(), getAlias(portalEntityField.entity(), portalEntityField.field()));
            }
        }
    }

    protected Class<VO> getVoClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    private String getAlias(Class<?> clazz, String fieldName) {
        String tableName = DbUtil.getTableName(clazz);
        String selectSqlName = DbUtil.getSelectSqlName(clazz, fieldName);
        return tableName + "." + selectSqlName;
    }

    @Override
    public Map<String, String> getAliasMap() {
        return aliasMap;
    }

    @Override
    public byte[] export(List<VO> dataList) throws IOException {
        PortalEntity portalEntity = getVoClass().getAnnotation(PortalEntity.class);
        Validator.assertNotNull(portalEntity, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        PortalWithColumnsRes portal = portalService.getPortalWithColumnsConfig(portalEntity.value()[0]);
        ExcelExportBO bo = new ExcelExportBO();
        bo.setTitle(portal.getDisplayName());
        for (SysPortalColumn column : portal.getColumns()) {
            bo.getColumnTitles().add(column.getDisplayName());
        }
        for (VO data : dataList) {
            Map<String, Object> hashMap = ReflectionUtil.getHashMap(data);
            List<String> records = new ArrayList<>();
            for (SysPortalColumn column : portal.getColumns()) {
                records.add(StringUtil.parse(hashMap.get(column.getProperty())));
            }
            bo.getRecords().add(records);
        }
        return export(bo);
    }

    private byte[] export(Object data) throws IOException {
        try (InputStream is = new ClassPathResource("excel/portalExportTemplate.xlsx").getInputStream()) {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                Context context = new Context();
                context.putVar("data", data);
                JxlsHelper jxlsHelper = JxlsHelper.getInstance();
                jxlsHelper.setUseFastFormulaProcessor(false);
                jxlsHelper.processTemplate(is, os, context);
                return os.toByteArray();
            }
        }
    }

    protected Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }
}
