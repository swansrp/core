package com.bidr.admin.service.excel.handler;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.service.excel.validation.PortalExcelValidationHandler;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.utils.ReflectionUtil;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Title: PortalExcelHandlerInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/15 22:56
 */
public interface PortalExcelHandlerInf<ENTITY> {

    /**
     * 导出excel模版
     *
     * @param os     文件输出流
     * @param portal 配置
     */
    default void templateExcel(OutputStream os, PortalWithColumnsRes portal) {
        String hiddenSheetName = "数据有效范围";
        List<List<String>> head = buildExcelHead(portal);
        ExcelWriter excelWriter = EasyExcel.write(os).build();
        WriteSheet data = EasyExcel.writerSheet(0, portal.getDisplayName()).head(head)
                .registerWriteHandler(new PortalExcelValidationHandler(portal, hiddenSheetName)).build();
        excelWriter.write(new ArrayList<>(), data);
        excelWriter.finish();
    }

    /**
     * 根据配置生成excel表头
     *
     * @param portal 配置
     * @return
     */
    default List<List<String>> buildExcelHead(PortalWithColumnsRes portal) {
        List<List<String>> head = new ArrayList<>();
        for (String column : ReflectionUtil.getFieldList(portal.getColumns(), SysPortalColumn::getDisplayName)) {
            head.add(Collections.singletonList(column));
        }
        return head;
    }

    /**
     * 当前行数据转换成entity
     *
     * @param portal      配置名称
     * @param data        当前行数据
     * @param entityCache 关联数据缓存
     * @return
     */
    ENTITY parseEntity(PortalWithColumnsRes portal, Map<Integer, String> data,
                       Map<String, Map<String, Object>> entityCache);


}
