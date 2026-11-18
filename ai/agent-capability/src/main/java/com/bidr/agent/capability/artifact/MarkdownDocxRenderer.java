package com.bidr.agent.capability.artifact;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: MarkdownDocxRenderer
 * Description: 简易 Markdown → docx 渲染器（P2a，零新三方——POI 由 tool/document 既有件供给）：
 * 只认监理日志这类文书实际会出现的面——ATX 标题（#~######）、段落、无序/有序列表、
 * 引用块、分隔线、围栏代码块、管道表格，行内 {@code **粗体**}、{@code *斜体*}、{@code \`行内代码\`}。
 * 输出 Word 正文样式：标题加粗降字号、表格实线边框＋表头加粗、代码 Courier New。
 * <p>
 * 不承诺完整 CommonMark（引用嵌套/多级表格对齐/HTML 块等不认）——模型按 save_supervision_log
 * 的技能包表样产出的是规整 Markdown，超出口法的行按普通段落原样保留，不丢字。
 * 渲染失败的异常由 {@link ArtifactService} 统一转人话错误。
 *
 * @author Sharp
 * @since 2026/11/13
 */
public final class MarkdownDocxRenderer {

    /** 行内标记 token：粗体/斜体/行内代码（贪婪序：先双星再单星，防 ** 被拆成两个 *） */
    private static final Pattern INLINE_MARK = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*|_(.+?)_|`([^`]+)`");

    /** 标题行：1-6 个 # + 空格 + 内容 */
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    /** 无序列表行 */
    private static final Pattern UNORDERED_ITEM = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");

    /** 有序列表行 */
    private static final Pattern ORDERED_ITEM = Pattern.compile("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$");

    /** 引用行 */
    private static final Pattern QUOTE = Pattern.compile("^\\s*>\\s?(.*)$");

    /** 分隔线（三个以上 -/_/* 或空格组合；表格分隔行在表解析内消化，不会进到这里） */
    private static final Pattern HR = Pattern.compile("^\\s*([-_*]\\s*){3,}$");

    /** 表格行：以 | 开头（容忍行尾可无 |） */
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|.*$");

    /** 表格头分隔行（| --- | :---: | 形态） */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|(\\s*:?-{2,}:?\\s*\\|)+\\s*$");

    /** 标题字号映射（H1→20pt 逐级降到 12pt） */
    private static final int[] HEADING_SIZES = {20, 16, 14, 13, 12, 12};

    private MarkdownDocxRenderer() {
    }

    /**
     * 渲染 Markdown 为 docx 字节（A4 默认页面，正文宋体系默认 10.5pt 由 Word 兜底）
     */
    public static byte[] render(String markdown) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
            for (int i = 0; i < lines.length; ) {
                String line = lines[i];
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    i++;
                    continue;
                }
                // 围栏代码块：``` 开闭之间原样保留（不做行内解析）
                if (trimmed.startsWith("```")) {
                    int end = findFenceEnd(lines, i + 1);
                    i = appendCodeBlock(document, lines, i + 1, end);
                    continue;
                }
                // 管道表格：连续 | 行（第二行是分隔行才算规整表格，否则按段落原样保留）
                if (TABLE_ROW.matcher(line).matches() && i + 1 < lines.length
                        && TABLE_SEPARATOR.matcher(lines[i + 1]).matches()) {
                    int end = i;
                    while (end < lines.length && TABLE_ROW.matcher(lines[end]).matches()) {
                        end++;
                    }
                    appendTable(document, lines, i, end);
                    i = end;
                    continue;
                }
                Matcher heading = HEADING.matcher(trimmed);
                if (heading.matches()) {
                    appendHeading(document, heading.group(2), heading.group(1).length());
                    i++;
                    continue;
                }
                if (HR.matcher(trimmed).matches()) {
                    appendDivider(document);
                    i++;
                    continue;
                }
                Matcher unordered = UNORDERED_ITEM.matcher(line);
                if (unordered.matches() && !TABLE_ROW.matcher(unordered.group(2)).matches()) {
                    appendListItem(document, unordered.group(2), unordered.group(1), "• ");
                    i++;
                    continue;
                }
                Matcher ordered = ORDERED_ITEM.matcher(line);
                if (ordered.matches()) {
                    appendListItem(document, ordered.group(3), ordered.group(1), ordered.group(2) + ". ");
                    i++;
                    continue;
                }
                Matcher quote = QUOTE.matcher(line);
                if (quote.matches()) {
                    appendQuote(document, quote.group(1));
                    i++;
                    continue;
                }
                appendParagraph(document, line.trim());
                i++;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    /** 找围栏结束行（```），未闭合则吃到文末 */
    private static int findFenceEnd(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (lines[i].trim().startsWith("```")) {
                return i;
            }
        }
        return lines.length;
    }

    /** 围栏代码块：单段 Courier New 9pt，行间软换行；返回消费到的下一行下标 */
    private static int appendCodeBlock(XWPFDocument document, String[] lines, int from, int end) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Courier New");
        run.setFontSize(9);
        for (int i = from; i < end; i++) {
            if (i > from) {
                run.addBreak();
            }
            run.setText(lines[i].replace('\t', ' '));
        }
        return Math.min(end + 1, lines.length);
    }

    /** 标题：加粗 + 降字号 + 段前段后留白 */
    private static void appendHeading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingBefore(240);
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(HEADING_SIZES[Math.min(level, HEADING_SIZES.length) - 1]);
    }

    /** 普通段落：行内标记解析出粗体/斜体/行内代码 run */
    private static void appendParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        appendInlineRuns(paragraph, text, false);
    }

    /** 列表项：项目符号/编号前缀 + 按缩进层级左移 */
    private static void appendListItem(XWPFDocument document, String text, String indent, String prefix) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(60);
        int level = indent.replace("\t", "  ").length() / 2;
        paragraph.setIndentationLeft(360 + Math.max(level, 0) * 360);
        paragraph.setIndentationHanging(240);
        XWPFRun marker = paragraph.createRun();
        marker.setText(prefix);
        appendInlineRuns(paragraph, text, false);
    }

    /** 引用块：斜体 + 左缩进 */
    private static void appendQuote(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        paragraph.setIndentationLeft(480);
        appendInlineRuns(paragraph, text, true);
    }

    /** 分隔线：居中细线字符段（比段落边框简单且 Word/记事本两见不糊） */
    private static void appendDivider(XWPFDocument document) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(120);
        paragraph.createRun().setText("───────────────");
    }

    /** 管道表格：首行＝表头加粗，实线边框；单元格保留行内标记解析，<br> 拆多段 */
    private static void appendTable(XWPFDocument document, String[] lines, int from, int end) {
        List<String[]> rows = new ArrayList<>();
        for (int i = from; i < end; i++) {
            String line = lines[i].trim();
            if (TABLE_SEPARATOR.matcher(line).matches() || i == from + 1) {
                // 头分隔行跳过（from+1 已在 matches 判定中挡住，这里双保险）
                continue;
            }
            rows.add(splitTableRow(line));
        }
        if (rows.isEmpty()) {
            return;
        }
        int cols = 0;
        for (String[] row : rows) {
            cols = Math.max(cols, row.length);
        }
        if (cols == 0) {
            return;
        }
        XWPFTable table = document.createTable(rows.size(), cols);
        applyBorders(table);
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow tableRow = table.getRow(r);
            for (int c = 0; c < cols; c++) {
                String value = c < rows.get(r).length ? rows.get(r)[c] : "";
                fillCell(tableRow.getCell(c), value, r == 0);
            }
        }
        // 表后补一个空段，避免表格紧贴下一段
        XWPFParagraph spacer = document.createParagraph();
        spacer.setSpacingAfter(60);
    }

    /** 拆一行表格为单元格（去掉首尾 |，按未转义的 | 切分；\| 还原为 |） */
    private static String[] splitTableRow(String line) {
        String body = line.startsWith("|") ? line.substring(1) : line;
        if (body.endsWith("|")) {
            body = body.substring(0, body.length() - 1);
        }
        String[] cells = body.split("(?<!\\\\)\\|", -1);
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cells[i].trim().replace("\\|", "|");
        }
        return cells;
    }

    /** 单元格填充：<br> 拆多段，每段行内解析；表头整格加粗 */
    private static void fillCell(XWPFTableCell cell, String value, boolean header) {
        String[] segments = value.split("<br\\s*/?>", -1);
        for (int i = 0; i < segments.length; i++) {
            XWPFParagraph paragraph = i == 0 ? cell.getParagraphs().get(0) : cell.addParagraph();
            paragraph.setSpacingAfter(0);
            if (header) {
                XWPFRun run = paragraph.createRun();
                run.setText(segments[i]);
                run.setBold(true);
            } else {
                appendInlineRuns(paragraph, segments[i], false);
            }
        }
    }

    /** 表格实线边框（外框＋内横竖线，1/2pt 单线） */
    private static void applyBorders(XWPFTable table) {
        CTTblBorders borders = table.getCTTbl().getTblPr().addNewTblBorders();
        borders.addNewTop().setVal(STBorder.SINGLE);
        borders.addNewBottom().setVal(STBorder.SINGLE);
        borders.addNewLeft().setVal(STBorder.SINGLE);
        borders.addNewRight().setVal(STBorder.SINGLE);
        borders.addNewInsideH().setVal(STBorder.SINGLE);
        borders.addNewInsideV().setVal(STBorder.SINGLE);
    }

    /**
     * 行内标记解析：**粗体** / __粗体__ / *斜体* / _斜体_ / `代码`（Courier New）。
     * 贪婪交替由 {@link #INLINE_MARK} 顺序保证（双星先试）。
     */
    private static void appendInlineRuns(XWPFParagraph paragraph, String text, boolean italic) {
        Matcher matcher = INLINE_MARK.matcher(text);
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() > index) {
                XWPFRun run = paragraph.createRun();
                run.setText(text.substring(index, matcher.start()));
                run.setItalic(italic);
            }
            String content = firstNonNull(matcher.group(1), matcher.group(2),
                    matcher.group(3), matcher.group(4), matcher.group(5));
            boolean code = matcher.group(5) != null;
            boolean bold = matcher.group(1) != null || matcher.group(2) != null;
            XWPFRun run = paragraph.createRun();
            run.setText(content);
            run.setBold(bold);
            run.setItalic(italic || (!bold && !code));
            if (code) {
                run.setFontFamily("Courier New");
            }
            index = matcher.end();
        }
        if (index < text.length()) {
            XWPFRun run = paragraph.createRun();
            run.setText(text.substring(index));
            run.setItalic(italic);
        }
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
