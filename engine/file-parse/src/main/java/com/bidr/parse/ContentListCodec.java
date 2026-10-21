package com.bidr.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ContentListCodec
 * Description: MinerU content_list（v1 扁平块）→ {@link PageBlock} 归一化（M17 棒 3 自
 * InkHub {@code MineruContentListCodec} 原样迁入，D-3 边界随层改判：引擎层出现后 pagemap
 * 是引擎产物组成部分——沙箱消费场景显式要它）。
 *
 * <p>采集口径（2026-09-20 补齐缺口，迁移时逐字保留）：表格走 {@code table_body}；
 * list 块条目按行拼接（定位探测只取首行——md 里条目带 {@code -} 前缀，整段探测必失配）；
 * 标题层级 {@code text_level} 落 {@code textLevel}；无题注的图片块用 {@code img_path}
 * 兜底定位（md 里的引用目标就是它）。题注字段数组/字符串两种形态都收。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
public final class ContentListCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContentListCodec() {
    }

    /**
     * content_list 归一化为 PageBlock，并计算块文本在 markdown 中的字符偏移。
     *
     * @param markdown     产物正文（探测偏移的坐标系）
     * @param contentList  content_list v1 原文 JSON（缺席＝抛——pagemap 是解析流水线的
     *                     必然产物，缺它等于产物残缺，现网同口径「缺 markdown 或 content_list」判失败）
     */
    public static List<PageBlock> normalize(String markdown, String contentList) {
        if (!StringUtils.hasText(markdown) || !StringUtils.hasText(contentList)) {
            throw new IllegalStateException("解析结果缺 markdown 或 content_list");
        }
        List<PageBlock> blocks = new ArrayList<>();
        try {
            JsonNode arr = MAPPER.readTree(contentList);
            int pos = 0;
            for (JsonNode node : arr) {
                String text = blockText(node).trim();
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                // 定位探测用首行：list 块在 md 里是"- 条目"多行形态，整段（含换行拼接）必失配
                String probeSource = text;
                int lineEnd = text.indexOf('\n');
                if (lineEnd > 0) {
                    probeSource = text.substring(0, lineEnd);
                }
                int probe = Math.min(probeSource.length(), 40);
                int idx = markdown.indexOf(probeSource.substring(0, probe), Math.max(0, pos - 50));
                if (idx < 0) {
                    idx = markdown.indexOf(probeSource.substring(0, Math.min(probeSource.length(), 20)));
                }
                if (idx < 0) {
                    continue;
                }
                PageBlock block = new PageBlock();
                block.setType(node.path("type").asText("text"));
                block.setText(text);
                block.setPageIdx(node.path("page_idx").asInt(0));
                JsonNode bbox = node.path("bbox");
                if (bbox.isArray() && bbox.size() == 4) {
                    block.setBbox(new double[]{bbox.get(0).asDouble(), bbox.get(1).asDouble(),
                            bbox.get(2).asDouble(), bbox.get(3).asDouble()});
                }
                int level = node.path("text_level").asInt(0);
                if (level > 0) {
                    block.setTextLevel(level);
                }
                block.setStart(idx);
                blocks.add(block);
                pos = idx + text.length();
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("content_list 解析失败", e);
        }
        return blocks;
    }

    /**
     * 取参与定位的块文本。
     *
     * <p>表格块的正文在 {@code table_body}（HTML）而非 {@code text}——不取它，表格就进不了
     * 页码映射，引用永远定位不到表格（真实 docx 实测 4/4 表格因此丢失定位）。HTML 原样返回：
     * 它与 markdown 里的表格逐字相同，块首探测才能命中；展示时再剥标签。</p>
     *
     * <p>list 块的条目在 {@code list_items} 数组（{@code text} 为空）——按行拼接进定位，
     * 否则整个列表进不了页码映射。无题注的图片块回退 {@code img_path}（md 引用目标，
     * {@code ![](images/x.jpg)} 内可逐字找到）。题注优先 {@code image_caption}/{@code table_caption}
     * （数组或字符串），兼容旧 {@code caption} 单数字段。</p>
     */
    static String blockText(JsonNode node) {
        String type = node.path("type").asText();
        if ("table".equals(type)) {
            String body = node.path("table_body").asText("").trim();
            if (StringUtils.hasText(body)) {
                return body;
            }
            String caption = captionOf(node);
            if (StringUtils.hasText(caption)) {
                return caption;
            }
        }
        if ("list".equals(type)) {
            String joined = textArrayOf(node.path("list_items"), "\n");
            if (StringUtils.hasText(joined)) {
                return joined;
            }
        }
        String text = node.path("text").asText("").trim();
        if (StringUtils.hasText(text)) {
            return text;
        }
        String caption = captionOf(node);
        if (StringUtils.hasText(caption)) {
            return caption;
        }
        return node.path("img_path").asText("").trim();
    }

    /** 题注：依次找 table_caption / image_caption / caption，数组（逐项拼接）或字符串都收 */
    private static String captionOf(JsonNode node) {
        for (String field : new String[]{"table_caption", "image_caption", "caption"}) {
            String value = textArrayOf(node.path(field), " ");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /** 数组节点逐项 trim 后拼接（跳过空项）；非数组按单值文本处理 */
    private static String textArrayOf(JsonNode array, String separator) {
        if (array == null || array.isMissingNode() || array.isNull()) {
            return "";
        }
        if (!array.isArray()) {
            return array.asText("").trim();
        }
        StringBuilder joined = new StringBuilder();
        for (JsonNode item : array) {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                if (joined.length() > 0) {
                    joined.append(separator);
                }
                joined.append(value);
            }
        }
        return joined.toString();
    }
}
