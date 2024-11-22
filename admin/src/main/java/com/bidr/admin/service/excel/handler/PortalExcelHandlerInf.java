package com.bidr.admin.service.excel.handler;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.service.excel.validation.ExcelValidationHandler;
import com.bidr.admin.service.excel.validation.PortalExcelValidationHandler;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: PortalExcelHandlerInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/15 22:56
 */
public interface PortalExcelHandlerInf {

    /**
     * 导出excel模版
     *
     * @param os     文件输出流
     * @param portal 配置
     */
    default void templateExcel(OutputStream os, PortalWithColumnsRes portal, Class<?> voClass) {
        String hiddenSheetName = "数据有效范围";
        List<List<String>> head = buildExcelHead(portal, voClass);
        ExcelWriter excelWriter = EasyExcel.write(os).build();
        WriteSheet data = EasyExcel.writerSheet(0, portal.getDisplayName()).head(head)
                .registerWriteHandler(new PortalExcelValidationHandler(portal, hiddenSheetName)).build();
        excelWriter.write(new ArrayList<>(), data);
        excelWriter.finish();
    }

    /**
     * 导出excel模版
     *
     * @param os 文件输出流
     */
    default void templateExcel(OutputStream os, Class<?> voClass) {
        String hiddenSheetName = "数据有效范围";
        List<List<String>> head = buildExcelHead(voClass);
        ExcelWriter excelWriter = EasyExcel.write(os).build();
        WriteSheet data = EasyExcel.writerSheet(0).head(head)
                .registerWriteHandler(new ExcelValidationHandler(voClass, hiddenSheetName)).build();
        excelWriter.write(new ArrayList<>(), data);
        excelWriter.finish();
    }

    /**
     * 根据配置生成excel表头
     *
     * @param portal  配置
     * @param voClazz 目标类
     * @return excel 表头
     */
    default List<List<String>> buildExcelHead(PortalWithColumnsRes portal, Class<?> voClazz) {
        List<List<String>> head = new ArrayList<>();
        if (FuncUtil.isNotEmpty(portal.getColumns())) {
            for (SysPortalColumn column : portal.getColumns()) {
                if (ReflectionUtil.existedField(voClazz, column.getProperty())) {
                    Field field = ReflectionUtil.getField(voClazz, column.getProperty());
                    if (field.getAnnotation(ExcelProperty.class) != null) {
                        head.add(Arrays.asList(field.getAnnotation(ExcelProperty.class).value()));
                    }
                }
            }
        }
        return head;
    }

    /**
     * 根据配置生成excel表头
     *
     * @param voClazz 目标类
     * @return excel 表头
     */
    default List<List<String>> buildExcelHead(Class<?> voClazz) {
        List<List<String>> head = new ArrayList<>();
        List<Field> fields = ReflectionUtil.getFields(voClazz);
        if (FuncUtil.isNotEmpty(fields)) {
            for (Field field : fields) {
                if (field.getAnnotation(ExcelProperty.class) != null) {
                    head.add(Arrays.asList(field.getAnnotation(ExcelProperty.class).value()));
                }
            }
        }
        return head;
    }


}
