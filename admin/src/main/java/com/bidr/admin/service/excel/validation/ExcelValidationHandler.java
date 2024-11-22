package com.bidr.admin.service.excel.validation;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.bidr.admin.config.PortalDictField;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.constant.dict.MetaTreeDict;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.service.cache.DictTreeCacheService;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.utils.excel.EasyExcelUtil;
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Title: PortalExcelValidationHandler
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/19 09:19
 */
@Slf4j
public class ExcelValidationHandler implements SheetWriteHandler {

    protected final String hiddenSheetName;
    private final List<Field> fields;

    public ExcelValidationHandler(Class<?> clazz, String hiddenSheetName) {
        this.fields = ReflectionUtil.getFields(clazz);
        this.hiddenSheetName = hiddenSheetName;
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Workbook workbook = writeSheetHolder.getSheet().getWorkbook();
        workbook.createSheet(hiddenSheetName);
        workbook.setSheetHidden(1, true);
        int columnIndex = -1;
        for (Field field : fields) {
            try {
                List<String> constraint;
                ExcelProperty excelPropertyAnno = field.getAnnotation(ExcelProperty.class);
                if (FuncUtil.isEmpty(excelPropertyAnno)) {
                    continue;
                }
                columnIndex++;
                String columnName = field.getName();
                ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
                if (FuncUtil.isNotEmpty(apiModelProperty)) {
                    columnName = apiModelProperty.value();
                }
                PortalDictField dictFieldAnno = field.getAnnotation(PortalDictField.class);
                if (FuncUtil.isNotEmpty(dictFieldAnno)) {
                    MetaDict metaDictAnno = dictFieldAnno.value().getAnnotation(MetaDict.class);
                    if (FuncUtil.isNotEmpty(metaDictAnno)) {
                        constraint = getDictConstraint(metaDictAnno.value());
                        setDataValidation(writeSheetHolder, columnIndex, columnName, constraint);
                        continue;
                    }
                    MetaTreeDict metaTreeDictAnno = dictFieldAnno.value().getAnnotation(MetaTreeDict.class);
                    if (FuncUtil.isNotEmpty(metaTreeDictAnno)) {
                        constraint = getTreeDictConstraint(metaTreeDictAnno.value());
                        setDataValidation(writeSheetHolder, columnIndex, columnName, constraint);
                    }
                }

            } catch (Exception e) {
                log.error("设置数据有效性异常", e);
            }
        }
    }

    protected List<String> getDictConstraint(String dictName) {
        List<KeyValueResVO> keyValue = BeanUtil.getBean(DictCacheService.class).getKeyValue(dictName);
        return ReflectionUtil.getFieldList(keyValue, KeyValueResVO::getLabel);
    }

    protected List<String> getTreeDictConstraint(String dictName) {
        List<KeyValueResVO> keyValue = BeanUtil.getBean(DictTreeCacheService.class).getAll(dictName);
        return ReflectionUtil.getFieldList(keyValue, KeyValueResVO::getLabel);
    }

    protected void setDataValidation(WriteSheetHolder writeSheetHolder, int columnIndex, String columnName,
                                     List<String> constraint) {
        DataValidationHelper validationHelper = writeSheetHolder.getSheet().getDataValidationHelper();
        CellRangeAddressList validationRange = new CellRangeAddressList(1, 60000, columnIndex, columnIndex);
        Sheet hidden = writeSheetHolder.getSheet().getWorkbook().getSheet(hiddenSheetName);
        int rowIndex = 0;
        for (String s : constraint) {
            Row row = hidden.getRow(rowIndex);
            if (row == null) {
                row = hidden.createRow(rowIndex);
            }
            row.createCell(columnIndex).setCellValue(s);
            rowIndex++;
        }
        String excelLine = EasyExcelUtil.getExcelLine(columnIndex);
        String refers = "=" + hiddenSheetName + "!$" + excelLine + "$1:$" + excelLine + "$" + (rowIndex);
        DataValidationConstraint validationConstraint = validationHelper.createFormulaListConstraint(refers);
        DataValidation dataValidation = validationHelper.createValidation(validationConstraint, validationRange);
        dataValidation.setShowErrorBox(true);
        dataValidation.createErrorBox("错误", "请选择正确的" + columnName);
        writeSheetHolder.getSheet().addValidationData(dataValidation);

    }
}
