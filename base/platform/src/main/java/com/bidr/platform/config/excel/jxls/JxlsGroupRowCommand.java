package com.bidr.platform.config.excel.jxls;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jxls.area.Area;
import org.jxls.command.AbstractCommand;
import org.jxls.command.Command;
import org.jxls.common.CellRef;
import org.jxls.common.Context;
import org.jxls.common.Size;
import org.jxls.transform.poi.PoiTransformer;
import org.jxls.util.Util;

/**
 * Title: JxlsGroupRowCommand
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/26 09:54
 */
@Slf4j
public class JxlsGroupRowCommand extends AbstractCommand {
    private Area area;
    private String collapseIf;

    public String getName() {
        return "groupRow";
    }

    public Size applyAt(CellRef cellRef, Context context) {
        Size resultSize = area.applyAt(cellRef, context);
        if (resultSize.equals(Size.ZERO_SIZE)) {
            return resultSize;
        }
        PoiTransformer transformer = (PoiTransformer) area.getTransformer();
        Workbook workbook = transformer.getWorkbook();
        Sheet sheet = workbook.getSheet(cellRef.getSheetName());
        int startRow = cellRef.getRow();
        int endRow = cellRef.getRow() + resultSize.getHeight() - 1;
        sheet.groupRow(startRow, endRow);
        if (collapseIf != null && collapseIf.trim().length() > 0) {
            Boolean collapseFlag = Util.isConditionTrue(getTransformationConfig().getExpressionEvaluator(), collapseIf, context);
            log.info(collapseFlag.toString());
            sheet.setRowGroupCollapsed(startRow, collapseFlag);
        }
        return resultSize;
    }

    @Override
    public Command addArea(Area area) {
        super.addArea(area);
        this.area = area;
        return this;
    }

    public void setCollapseIf(String collapseIf) {
        this.collapseIf = collapseIf;
    }
}
