package com.bidr.parse;

/**
 * Title: ParseEnrichContext
 * Description: 增强腿的执行上下文（引擎流程在 ENRICH 阶段组装）：产物键与路线信息——
 * 腿自持 IO（读正文/页码映射、写回正文都按这些键走对象存储），引擎不解释键里的坐标。
 *
 * @author Sharp
 * @since 2026-10-21
 */
public final class ParseEnrichContext {

    private final String sourceSha;

    private final String anchorSha;

    private final int variant;

    private final String markdownKey;

    private final String pageMapKey;

    private final String route;

    private final int imageCount;

    private final int pageImageCount;

    public ParseEnrichContext(String sourceSha, String anchorSha, int variant, String markdownKey,
            String pageMapKey, String route, int imageCount, int pageImageCount) {
        this.sourceSha = sourceSha;
        this.anchorSha = anchorSha;
        this.variant = variant;
        this.markdownKey = markdownKey;
        this.pageMapKey = pageMapKey;
        this.route = route;
        this.imageCount = imageCount;
        this.pageImageCount = pageImageCount;
    }

    /** 上传原件内容 sha（断点与转换键的寻址源） */
    public String getSourceSha() {
        return sourceSha;
    }

    /** 解析锚（office 已转 PDF 时为 PDF 的 sha，未转换回落原件） */
    public String getAnchorSha() {
        return anchorSha;
    }

    /** 键代数段（提交方给的透传值，引擎不当业务版本解释——D-1） */
    public int getVariant() {
        return variant;
    }

    /** 正文产物键（{@code parsed/{sha}/v{n}.md}） */
    public String getMarkdownKey() {
        return markdownKey;
    }

    /** 页码映射键（无映射腿＝null——直通/内置降级无页） */
    public String getPageMapKey() {
        return pageMapKey;
    }

    /** 转录路线（passthrough / builtin / 异步腿名） */
    public String getRoute() {
        return route;
    }

    /** 产物内联图张数 */
    public int getImageCount() {
        return imageCount;
    }

    /** 页图张数（D-11 减掉 pageImage 时为 0） */
    public int getPageImageCount() {
        return pageImageCount;
    }
}
