package com.bidr.admin.service.excel.validation;

import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.kernel.controller.inf.AdminControllerInf;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.bidr.platform.utils.excel.EasyExcelUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;

import java.util.List;

/**
 * Title: PortalExcelValidationHandler
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/19 09:19
 */
@Slf4j
public class PortalExcelValidationHandler implements SheetWriteHandler {

    private final PortalWithColumnsRes portal;
    private final String hiddenSheetName;

    public PortalExcelValidationHandler(PortalWithColumnsRes portal, String hiddenSheetName) {
        this.portal = portal;
        this.hiddenSheetName = hiddenSheetName;
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Workbook workbook = writeSheetHolder.getSheet().getWorkbook();
        workbook.createSheet(hiddenSheetName);
        workbook.setSheetHidden(1, true);
        int columnIndex = 0;
        for (SysPortalColumn column : portal.getColumns()) {
            try {
                List<String> constraint;
                switch (DictEnumUtil.getEnumByValue(column.getFieldType(), PortalFieldDict.class)) {
                    case ENUM:
                        constraint = getDictConstraint(column);
                        break;
                    case ENTITY:
                        constraint = getEntityConstraint(column);
                        break;
                    default:
                        constraint = null;
                        break;
                }
                if (FuncUtil.isNotEmpty(constraint)) {
                    setDataValidation(writeSheetHolder, columnIndex, column, constraint);
                }
                columnIndex++;
            } catch (Exception e) {
                log.error("设置数据有效性异常", e);
            }
        }
    }

    private List<String> getDictConstraint(SysPortalColumn column) {
        List<KeyValueResVO> keyValue = BeanUtil.getBean(DictCacheService.class).getKeyValue(column.getReference());
        return ReflectionUtil.getFieldList(keyValue, KeyValueResVO::getLabel);
    }

    private List<String> getEntityConstraint(SysPortalColumn column) throws ClassNotFoundException {
        List<String> constraint;
        SysPortal entityPortal = BeanUtil.getBean(SysPortalService.class)
                .getByName(column.getReference(), PortalConfigContext.getPortalConfigRoleId());
        AdvancedQueryReq req = new AdvancedQueryReq();
        if (FuncUtil.isNotEmpty(column.getEntityCondition())) {
            AdvancedQuery entityCondition = JsonUtil.readJson(column.getEntityCondition(), AdvancedQuery.class);
            req.setCondition(entityCondition);
        }
        req.setPageSize(100L);
        Page page = ((AdminControllerInf) BeanUtil.getBean(
                StringUtil.firstLowerCamelCase(entityPortal.getBean()))).advancedQuery(req);
        if (page.getPages() > 1) {
            log.warn("目标实体数量过多:{} 放弃excel数据校验", page.getTotal());
            return null;
        } else {
            constraint = ReflectionUtil.getFieldList(page.getRecords(), entityPortal.getNameColumn(), String.class);
            return constraint;
        }

    }

    private void setDataValidation(WriteSheetHolder writeSheetHolder, int columnIndex, SysPortalColumn column,
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
        dataValidation.createErrorBox("错误", "请选择正确的" + column.getDisplayName());
        writeSheetHolder.getSheet().addValidationData(dataValidation);

    }
}
