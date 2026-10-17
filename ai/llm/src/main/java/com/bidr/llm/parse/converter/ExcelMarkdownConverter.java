package com.bidr.llm.parse.converter;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * xlsx/xls → Markdown 转换器：每个 Sheet 输出一个 Markdown 表格（多 Sheet 时加二级标题区分），
 * 首行视为表头；单元格按显示值格式化（日期/数字格式不丢），超长 Sheet 截断并注明。
 *
 * @author Sharp
 */
public final class ExcelMarkdownConverter {

    /**
     * 每个 Sheet 输出的行数上限，防止大表撑爆提示词
     */
    private static final int MAX_ROWS_PER_SHEET = 1000;

    private ExcelMarkdownConverter() {
    }

    public static String convert(byte[] bytes) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            DataFormatter formatter = new DataFormatter();
            boolean multiSheet = workbook.getNumberOfSheets() > 1;
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                appendSheet(workbook.getSheetAt(s), formatter, multiSheet, sb);
            }
            return sb.toString().trim();
        }
    }

    private static void appendSheet(Sheet sheet, DataFormatter formatter, boolean multiSheet, StringBuilder sb) {
        List<List<String>> rows = new ArrayList<>();
        int maxCol = 0;
        int lastRow = Math.min(sheet.getLastRowNum(), MAX_ROWS_PER_SHEET);
        for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                cells.add(escapeCell(value));
            }
            if (cells.stream().allMatch(String::isEmpty)) {
                continue;
            }
            maxCol = Math.max(maxCol, cells.size());
            rows.add(cells);
        }
        if (rows.isEmpty()) {
            return;
        }
        if (multiSheet) {
            sb.append("## ").append(sheet.getSheetName()).append("\n\n");
        }
        for (int i = 0; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            sb.append('|');
            for (int c = 0; c < maxCol; c++) {
                sb.append(' ').append(c < cells.size() ? cells.get(c) : "").append(" |");
            }
            sb.append('\n');
            if (i == 0) {
                sb.append('|');
                for (int c = 0; c < maxCol; c++) {
                    sb.append(" --- |");
                }
                sb.append('\n');
            }
        }
        if (sheet.getLastRowNum() > MAX_ROWS_PER_SHEET) {
            sb.append("\n> 仅展示前 ").append(MAX_ROWS_PER_SHEET)
                    .append(" 行，该表共 ").append(sheet.getLastRowNum() + 1).append(" 行\n");
        }
        sb.append('\n');
    }

    /**
     * 单元格转义：管道符转义防破表，换行收敛为单行（\r\n 转 &lt;br&gt; 保留语义，其余空白化）
     */
    private static String escapeCell(String value) {
        return value.replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace('\n', ' ')
                .replace('\r', ' ');
    }
}
