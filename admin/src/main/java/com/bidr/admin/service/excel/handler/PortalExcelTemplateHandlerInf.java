package com.bidr.admin.service.excel.handler;

import com.alibaba.excel.annotation.ExcelProperty;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.bo.excel.ExcelExportBO;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.SneakyThrows;
import org.jxls.common.Context;
import org.jxls.util.JxlsHelper;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Title: PortalExcelTemplateHandlerInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/07 13:50
 */
public interface PortalExcelTemplateHandlerInf {
    /**
     * 根据指定类生成excel模版
     *
     * @param excelVOClass 类
     * @return 模版
     */
    @SneakyThrows
    default byte[] export(Class<?> excelVOClass) {
        List<Field> fields = ReflectionUtil.getFields(excelVOClass);
        if (FuncUtil.isNotEmpty(fields)) {
            ExcelExportBO data = new ExcelExportBO();
            for (Field field : fields) {
                ExcelProperty excel = field.getAnnotation(ExcelProperty.class);
                ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                if (FuncUtil.isNotEmpty(excel) && FuncUtil.isNotEmpty(excel.value()[0])) {
                    data.getColumnTitles().add(excel.value()[0]);
                } else if (FuncUtil.isNotEmpty(apiModelProperty)) {
                    data.getColumnTitles().add(apiModelProperty.value());
                } else if (FuncUtil.isNotEmpty(jsonProperty)) {
                    data.getColumnTitles().add(jsonProperty.value());
                } else {
                    data.getColumnTitles().add(field.getName());
                }
            }
            return export(data);

        }
        return null;
    }

    /**
     * 根据数据生成excel
     *
     * @param data 数据
     * @return excel
     */
    @SneakyThrows
    default byte[] export(ExcelExportBO data) {
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
}
