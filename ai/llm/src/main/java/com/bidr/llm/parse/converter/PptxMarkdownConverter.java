package com.bidr.llm.parse.converter;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * pptx → Markdown 转换器：每页一个二级标题，页内文本框与表格按出现顺序输出；
 * 无内容的页跳过。
 *
 * @author Sharp
 */
public final class PptxMarkdownConverter {

    private PptxMarkdownConverter() {
    }

    public static String convert(byte[] bytes) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            int index = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                index++;
                StringBuilder slideContent = new StringBuilder();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTable) {
                        appendTable((XSLFTable) shape, slideContent);
                    } else if (shape instanceof XSLFTextShape) {
                        String text = ((XSLFTextShape) shape).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            slideContent.append(text.trim()).append("\n\n");
                        }
                    }
                }
                if (slideContent.length() > 0) {
                    sb.append("## 幻灯片 ").append(index).append("\n\n").append(slideContent);
                }
            }
            return sb.toString().trim();
        }
    }

    private static void appendTable(XSLFTable table, StringBuilder sb) {
        List<XSLFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.stream().mapToInt(row -> row.getCells().size()).max().orElse(0);
        if (cols == 0) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            XSLFTableRow row = rows.get(i);
            sb.append('|');
            for (int c = 0; c < cols; c++) {
                String cell = c < row.getCells().size() ? cellText(row.getCells().get(c)) : "";
                sb.append(' ').append(cell).append(" |");
            }
            sb.append('\n');
            if (i == 0) {
                sb.append('|');
                for (int c = 0; c < cols; c++) {
                    sb.append(" --- |");
                }
                sb.append('\n');
            }
        }
        sb.append('\n');
    }

    private static String cellText(XSLFTableCell cell) {
        String text = cell.getText();
        return text == null ? "" : text.trim().replace('|', ' ').replace('\n', ' ');
    }
}
