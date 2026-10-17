package com.bidr.llm.parse.converter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * html → Markdown 转换器（Jsoup）：块级元素按语义映射（标题/段落/列表/表格/代码块/引用），
 * 行内元素保留加粗、斜体、行内代码、链接、图片语法；script/style 直接剔除。
 *
 * @author Sharp
 */
public final class HtmlMarkdownConverter {

    /**
     * 行内元素集合：出现在块级上下文中时按行内文本拼接，不产生换行
     */
    private static final Set<String> INLINE_TAGS = new HashSet<>(Arrays.asList(
            "span", "a", "b", "strong", "i", "em", "u", "s", "small", "code", "sub", "sup", "label", "font"));

    private HtmlMarkdownConverter() {
    }

    public static String convert(String html) {
        Document document = Jsoup.parse(html);
        document.select("script, style, noscript, iframe").remove();
        StringBuilder sb = new StringBuilder();
        renderBlock(document.body(), sb);
        return sb.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    /**
     * 块级遍历：按标签语义输出，未知块级标签递归其子节点
     */
    private static void renderBlock(Element element, StringBuilder sb) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).text().trim();
                if (!text.isEmpty()) {
                    sb.append(text).append(' ');
                }
                continue;
            }
            if (!(node instanceof Element)) {
                continue;
            }
            Element child = (Element) node;
            String tag = child.tagName().toLowerCase(Locale.ROOT);
            if (INLINE_TAGS.contains(tag)) {
                sb.append(inline(child));
                continue;
            }
            switch (tag) {
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6": {
                    int level = Integer.parseInt(tag.substring(1));
                    for (int i = 0; i < level; i++) {
                        sb.append('#');
                    }
                    sb.append(' ').append(inline(child)).append("\n\n");
                    break;
                }
                case "p":
                    sb.append(inline(child)).append("\n\n");
                    break;
                case "ul":
                    renderList(child, sb, false);
                    break;
                case "ol":
                    renderList(child, sb, true);
                    break;
                case "table":
                    renderTable(child, sb);
                    break;
                case "pre":
                    sb.append("```\n").append(child.wholeText().trim()).append("\n```\n\n");
                    break;
                case "blockquote":
                    sb.append("> ").append(inline(child)).append("\n\n");
                    break;
                case "hr":
                    sb.append("---\n\n");
                    break;
                case "br":
                    sb.append('\n');
                    break;
                default:
                    renderBlock(child, sb);
            }
        }
    }

    private static void renderList(Element list, StringBuilder sb, boolean ordered) {
        int index = 1;
        for (Element li : list.children()) {
            if (!li.tagName().equalsIgnoreCase("li")) {
                continue;
            }
            String marker = ordered ? (index++ + ". ") : "- ";
            sb.append(marker).append(inline(li)).append('\n');
        }
        sb.append('\n');
    }

    private static void renderTable(Element table, StringBuilder sb) {
        List<List<String>> rows = new ArrayList<>();
        for (Element tr : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : tr.select("th, td")) {
                cells.add(inline(cell).replace("|", "\\|").replace('\n', ' ').trim());
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            sb.append('|');
            for (int c = 0; c < cols; c++) {
                sb.append(' ').append(c < cells.size() ? cells.get(c) : "").append(" |");
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

    /**
     * 行内内容拼接：加粗/斜体/行内代码/链接/图片按 Markdown 语法包裹，其余递归取文本
     */
    private static String inline(Element element) {
        StringBuilder sb = new StringBuilder();
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode) {
                sb.append(((TextNode) node).text());
                continue;
            }
            if (!(node instanceof Element)) {
                continue;
            }
            Element child = (Element) node;
            String tag = child.tagName().toLowerCase(Locale.ROOT);
            String inner = inline(child);
            switch (tag) {
                case "br":
                    sb.append(' ');
                    break;
                case "strong":
                case "b":
                    if (!inner.isEmpty()) {
                        sb.append("**").append(inner).append("**");
                    }
                    break;
                case "em":
                case "i":
                    if (!inner.isEmpty()) {
                        sb.append('*').append(inner).append('*');
                    }
                    break;
                case "code":
                    if (!inner.isEmpty()) {
                        sb.append('`').append(inner).append('`');
                    }
                    break;
                case "a": {
                    String href = child.attr("href");
                    if (href.isEmpty()) {
                        sb.append(inner);
                    } else {
                        sb.append('[').append(inner).append("](").append(href).append(')');
                    }
                    break;
                }
                case "img": {
                    String src = child.attr("src");
                    if (!src.isEmpty()) {
                        sb.append("![").append(child.attr("alt")).append("](").append(src).append(')');
                    }
                    break;
                }
                default:
                    sb.append(inner);
            }
        }
        return sb.toString().trim();
    }
}
