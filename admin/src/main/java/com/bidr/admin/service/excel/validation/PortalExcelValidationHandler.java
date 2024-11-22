package com.bidr.admin.service.excel.validation;

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
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

/**
 * Title: PortalExcelValidationHandler
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/19 09:19
 */
@Slf4j
public class PortalExcelValidationHandler extends ExcelValidationHandler {

    private final PortalWithColumnsRes portal;

    public PortalExcelValidationHandler(PortalWithColumnsRes portal, String hiddenSheetName) {
        super(Object.class, hiddenSheetName);
        this.portal = portal;
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
                    case ENUM_MULTI_IN_ONE:
                        constraint = getDictConstraint(column.getReference());
                        break;
                    case TREE:
                    case TREE_MULTI_IN_ONE:
                        constraint = getTreeDictConstraint(column.getReference());
                        break;
                    case ENTITY:
                        constraint = getEntityConstraint(column);
                        break;
                    default:
                        constraint = null;
                        break;
                }
                if (FuncUtil.isNotEmpty(constraint)) {
                    setDataValidation(writeSheetHolder, columnIndex, column.getDisplayName(), constraint);
                }
                columnIndex++;
            } catch (Exception e) {
                log.error("设置数据有效性异常", e);
            }
        }
    }

    private List<String> getEntityConstraint(SysPortalColumn column) {
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
}
