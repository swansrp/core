package com.bidr.llm.parse.converter;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * docx → Markdown 转换器：按文档体顺序遍历段落与表格，
 * 标题样式映射为 # 级标题、列表映射为 - 项、表格输出 Markdown 表格语法。
 *
 * @author Sharp
 */
public final class DocxMarkdownConverter {

    /**
     * heading 类样式 id（如 heading1、Heading2）
     */
    private static final Pattern HEADING_STYLE = Pattern.compile("heading\\s*(\\d)", Pattern.CASE_INSENSITIVE);

    private DocxMarkdownConverter() {
    }

    public static String convert(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph) {
                    appendParagraph((XWPFParagraph) element, sb);
                } else if (element instanceof XWPFTable) {
                    appendTable((XWPFTable) element, sb);
                }
            }
            return sb.toString().trim();
        }
    }

    private static void appendParagraph(XWPFParagraph paragraph, StringBuilder sb) {
        String text = paragraph.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        int headingLevel = headingLevel(paragraph);
        if (headingLevel > 0) {
            for (int i = 0; i < headingLevel; i++) {
                sb.append('#');
            }
            sb.append(' ').append(text.trim()).append("\n\n");
            return;
        }
        if (paragraph.getNumIlvl() != null) {
            sb.append("- ").append(text.trim()).append('\n');
            return;
        }
        sb.append(text.trim()).append("\n\n");
    }

    /**
     * 从样式 id 推断标题级别：纯数字（1-6）或 headingN 形式，无法识别返回 0
     */
    private static int headingLevel(XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return 0;
        }
        if (styleId.matches("[1-6]")) {
            return Integer.parseInt(styleId);
        }
        Matcher matcher = HEADING_STYLE.matcher(styleId);
        if (matcher.find()) {
            return Math.min(Integer.parseInt(matcher.group(1)), 6);
        }
        return 0;
    }

    private static void appendTable(XWPFTable table, StringBuilder sb) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.stream().mapToInt(row -> row.getTableCells().size()).max().orElse(0);
        if (cols == 0) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            sb.append('|');
            for (int c = 0; c < cols; c++) {
                String cell = c < row.getTableCells().size() ? cellText(row.getCell(c)) : "";
                sb.append(' ').append(cell).append(" |");
            }
            sb.append('\n');
            if (i == 0) {
                appendSeparator(sb, cols);
            }
        }
        sb.append('\n');
    }

    static void appendSeparator(StringBuilder sb, int cols) {
        sb.append('|');
        for (int c = 0; c < cols; c++) {
            sb.append(" --- |");
        }
        sb.append('\n');
    }

    /**
     * 单元格文本：多段落用 &lt;br&gt; 连接，管道符转义，保证 Markdown 表格不破行
     */
    private static String cellText(XWPFTableCell cell) {
        return cell.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .filter(t -> t != null && !t.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.joining("<br>"))
                .replace("|", "\\|");
    }
}
