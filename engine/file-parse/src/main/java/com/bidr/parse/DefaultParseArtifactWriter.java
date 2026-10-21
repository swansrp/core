package com.bidr.parse;

import com.bidr.blob.BlobStore;
import com.bidr.document.spi.AsyncTranscribeLeg;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: DefaultParseArtifactWriter
 * Description: 产物落盘的内置默认实现（M17 棒 3，自 InkHub {@code ParseArtifactService}
 * 逐字迁入——同输入逐字节一致的 A/B 判据靠"搬家不改写"兑现）。
 *
 * <p><b>图片引用为什么是占位（2026-09-19 定案）</b>：外部引擎产物里的图片是
 * {@code images/xxx.jpg} 相对路径，谁都无法访问，故先入库到对象存储；
 * 早期做法是再把正文里的引用改写成预签名 URL，实测知识库引擎会拉图、缩写引用并注入自己的
 * 描述 → 两边文本不一致、引擎偏移 83% 不可用，且签名过期后正文里的链接全部失效。
 * 现改为稳定占位（{@link BlobStore#imagePlaceholder} 的产物形态，scheme 正源常量在
 * BlobStore）：两边文本逐字相同（引擎偏移 100% 可用），
 * 可访问 URL 由读取边缘现签。</p>
 *
 * <p><b>页码映射格式（D8）</b>：块按 start 升序，每个块给 {@code start/end/pageIdx}——
 * 命中词的全文偏移落在哪个块，就取该块页码。文本不冗余存储：
 * {@code md.substring(start, end)} 即可还原，避免产物体积翻倍。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
public class DefaultParseArtifactWriter implements ParseArtifactWriter {

    /** markdown 图片引用：{@code ![alt](images/x.jpg "title")} → 保留 alt 与 title，替换目标 */
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)([^)]*)\\)");

    private final BlobStore storage;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultParseArtifactWriter(BlobStore storage) {
        this.storage = storage;
    }

    /** 外部转录腿产物固化：图片入库+改写 → 块偏移重定位 → 正文与页码映射落库 */
    @Override
    public Written transcribe(String anchorSha, int variant, AsyncTranscribeLeg.Artifact artifact) {
        List<PageBlock> blocks = ContentListCodec.normalize(artifact.getMarkdown(), artifact.getContentList());
        Map<String, String> urls = storeImages(anchorSha, imagesOf(artifact));
        String markdown = rewriteImages(artifact.getMarkdown(), urls);

        // 块偏移必须跟随<b>改写后</b>的正文：引擎检索用的就是这份（占位比引擎相对路径长，
        // 不重定位则图片引用之后的块全部漂移——2026-09-20 回归测试钉死）
        relocateOffsets(blocks, markdown, urls);

        String markdownKey = BlobStore.parsedMarkdownKey(anchorSha, variant);
        String pageMapKey = BlobStore.pageMapKey(anchorSha, variant);
        String pageMap = pageMapJson(blocks);
        // 🔴 落库顺序：pagemap 先于 md——断点判据以 md 在场为准（md 在＝映射必已写），
        // 反序会在两写之间崩溃时留下「有正文无映射」的静默降级产物
        storage.put(pageMapKey, bytes(pageMap), BlobStore.contentTypeOf("x.json"));
        storage.put(markdownKey, bytes(markdown), BlobStore.contentTypeOf("x.md"));
        log.info("解析产物已落库: sha={} v={} 块={} 页={} 图={} 正文={} 字节",
                anchorSha, variant, ordered(blocks).size(), pageCount(blocks),
                urls.size(), markdown.length());
        return new Written(markdownKey, pageMapKey, pageCount(blocks), ordered(blocks).size(), urls.size());
    }

    /** 内置直读腿产物固化：markdown 落 key（无页码映射，引用降级无页） */
    @Override
    public Written builtin(String anchorSha, int variant, String markdown) {
        String markdownKey = BlobStore.parsedMarkdownKey(anchorSha, variant);
        storage.put(markdownKey, bytes(markdown), BlobStore.contentTypeOf("x.md"));
        return new Written(markdownKey, null, 0, 0, 0);
    }

    /**
     * 按给定正文重算块的 start（引擎归一化的偏移是"改写前"坐标——占位替换/图注注入都会移动它）。
     * 探测算法与引擎侧一致：块文本首行前 40 字符、游标回退 50、全局 20 字符兜底；
     * 图片块的文本可能是 img_path（{@code images/x.jpg}），此时改探测占位 URL 本身。
     * 探测失败保留原 start（宁可沿用旧坐标也不清零）。
     */
    static void relocateOffsets(List<PageBlock> blocks, String markdown, Map<String, String> urlByName) {
        int pos = 0;
        for (PageBlock block : ordered(blocks)) {
            int idx = probe(markdown, block.getText(), urlByName, pos);
            if (idx >= 0) {
                block.setStart(idx);
                pos = idx + (block.getText() == null ? 0 : block.getText().length());
            }
        }
    }

    private static int probe(String markdown, String text, Map<String, String> urlByName, int pos) {
        if (!StringUtils.hasText(text)) {
            return -1;
        }
        String placeholder = urlByName == null ? null : urlByName.get(text);
        String[] candidates = placeholder != null
                ? new String[]{placeholder, firstLine(text)}
                : new String[]{firstLine(text)};
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            String head = candidate.length() > 40 ? candidate.substring(0, 40) : candidate;
            int idx = markdown.indexOf(head, Math.max(0, pos - 50));
            if (idx < 0 && candidate.length() > 20) {
                idx = markdown.indexOf(candidate.substring(0, 20));
            }
            if (idx >= 0) {
                return idx;
            }
        }
        return -1;
    }

    private static String firstLine(String text) {
        int nl = text.indexOf('\n');
        return nl > 0 ? text.substring(0, nl) : text;
    }

    /** 图片逐张入库，返回"产物内路径 → 正文引用形态"映射（供 markdown 改写） */
    private Map<String, String> storeImages(String anchorSha, List<AsyncTranscribeLeg.Image> images) {
        Map<String, String> refs = new HashMap<>();
        if (images == null || images.isEmpty()) {
            return refs;
        }
        for (AsyncTranscribeLeg.Image image : images) {
            if (image == null || !StringUtils.hasText(image.getName()) || image.getData() == null) {
                continue;
            }
            String key = BlobStore.imageKey(anchorSha, image.getName());
            storage.put(key, image.getData(), BlobStore.contentTypeOf(image.getName()));
            // 正文存稳定占位而非预签名 URL：签名在读取边缘现签（见 BlobStore.imagePlaceholder）
            refs.put(image.getName(), BlobStore.imagePlaceholder(anchorSha, image.getName()));        }
        return refs;
    }

    private static List<AsyncTranscribeLeg.Image> imagesOf(AsyncTranscribeLeg.Artifact artifact) {
        return artifact.getImages() == null ? new ArrayList<>() : artifact.getImages();
    }

    /**
     * markdown 图片引用改写为给定 URL。
     *
     * <p>未命中映射（产物里没有对应图片文件）的引用原样保留——宁可留个相对路径让人看见，
     * 也不要静默删掉正文里的图，更不要把坏链接写进去。</p>
     */
    static String rewriteImages(String markdown, Map<String, String> urlByName) {
        if (!StringUtils.hasText(markdown) || urlByName == null || urlByName.isEmpty()) {
            return markdown;
        }
        Matcher matcher = MD_IMAGE.matcher(markdown);
        StringBuffer out = new StringBuffer();
        int rewritten = 0;
        while (matcher.find()) {
            String url = urlByName.get(matcher.group(2));
            if (url == null) {
                continue;
            }
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement("![" + matcher.group(1) + "](" + url + matcher.group(3) + ")"));
            rewritten++;
        }
        matcher.appendTail(out);
        if (rewritten < urlByName.size()) {
            log.warn("有图片未在正文中被引用: 已改写={} 图片数={}", rewritten, urlByName.size());
        }
        return out.toString();
    }

    /** 页码映射 JSON（D8）：pageCount + 有序块 [start,end/pageIdx/bbox/textLevel] */
    String pageMapJson(List<PageBlock> blocks) {
        List<PageBlock> ordered = ordered(blocks);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("pageCount", pageCount(ordered));
        ArrayNode array = root.putArray("blocks");
        for (PageBlock block : ordered) {
            ObjectNode node = array.addObject();
            node.put("type", block.getType());
            node.put("pageIdx", block.getPageIdx());
            node.put("start", block.getStart());
            node.put("end", block.getStart() + (block.getText() == null ? 0 : block.getText().length()));
            if (block.getBbox() != null && block.getBbox().length == 4) {
                ArrayNode bbox = node.putArray("bbox");
                for (double v : block.getBbox()) {
                    bbox.add(v);
                }
            }
            if (block.getTextLevel() != null) {
                // 章节切分与"按章节引用"的输入（切分器归业务）；非标题块不写，保持产物紧凑
                node.put("textLevel", block.getTextLevel());
            }
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("页码映射序列化失败", e);
        }
    }

    /** 有效块：丢弃未定位（start<0）与空文本块，按 start 升序（引擎已保证，这里再兜一层） */
    private static List<PageBlock> ordered(List<PageBlock> blocks) {
        List<PageBlock> ordered = new ArrayList<>();
        if (blocks == null) {
            return ordered;
        }
        for (PageBlock block : blocks) {
            if (block != null && block.getStart() >= 0 && StringUtils.hasText(block.getText())) {
                ordered.add(block);
            }
        }
        ordered.sort(Comparator.comparingInt(PageBlock::getStart));
        return ordered;
    }

    /** 文档页数：由块的最大页码推导（引擎的 page_idx 从 0 起） */
    private static int pageCount(List<PageBlock> blocks) {
        int max = -1;
        for (PageBlock block : blocks) {
            if (block != null && block.getStart() >= 0 && block.getPageIdx() > max) {
                max = block.getPageIdx();
            }
        }
        return max + 1;
    }

    private static byte[] bytes(String text) {
        return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    }
}
