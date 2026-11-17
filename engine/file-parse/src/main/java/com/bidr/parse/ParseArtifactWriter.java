package com.bidr.parse;

import com.bidr.document.spi.AsyncTranscribeLeg;

/**
 * Title: ParseArtifactWriter
 * Description: 解析产物的落盘定制槽（引擎专用 SPI，引擎层自带默认实现）——
 * 把转录腿吐出的材料固化成键形产物（{@code parsed/{sha}/v{n}.md}、{@code pagemap/{sha}/v{n}.json}、
 * {@code image/{sha}/{name}}）。业务注册自己的 Bean 即整体接管落盘（R-5 自证腿：消费方 pom
 * 必引引擎才有实现位）；不注册＝内置默认（图片入库＋占位改写＋偏移重定位）。
 *
 * <p>契约：入参 {@code anchorSha}（解析锚）与 {@code variant}（键代数段，提交方给的透传值，
 * 引擎不当业务版本解释）直接决定产物键——实现不得改键形（R-2 golden 钉死）；
 * markdown 里的图片引用写稳定占位（{@link com.bidr.blob.BlobStore#imagePlaceholder}，scheme 由业务装配供给——协议值业务所有）。</p>
 *
 * <p><b>namespace（2026-11-17 temp 前缀定案）</b>：提交方在 payload 带 {@code keyNamespace} 时，
 * md/pagemap 产物键整体挂到该前缀下（{@link com.bidr.blob.BlobStore#namespaced}——临时轨
 * {@code temp/material} 由此吃桶级 ILM）；空＝主命名空间、键逐字节不变。<b>内嵌图恒落
 * {@code image/} 主命名空间</b>——跨文档耐用资产不随临时轨过期。页图（{@code thumb/}）不
 * 参与包装：临时轨恒裁剪 {@code pageImage}，不会有临时页图。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
public interface ParseArtifactWriter {

    /**
     * 外部转录腿（MinerU 形：markdown＋原文归一清单＋图片字节）的产物固化。
     * 内置实现：图片逐张入库 → 引用改占位 → 块偏移按改写后正文重定位 → md/pagemap 落 key。
     */
    Written transcribe(String anchorSha, int variant, String namespace, AsyncTranscribeLeg.Artifact artifact);

    /**
     * 内置直读腿的产物固化（无页码映射：内置腿出不了页块＝引用降级无页，
     * {@code pageMapKey} 为 null，下游按该列判空降级）。
     */
    Written builtin(String anchorSha, int variant, String namespace, String markdown);

    /** 落盘结果（键形即交付通道：业务观察者自任务行快照取键回写业务行） */
    final class Written {

        private final String markdownKey;

        private final String pageMapKey;

        private final int pageCount;

        private final int blockCount;

        private final int imageCount;

        public Written(String markdownKey, String pageMapKey, int pageCount, int blockCount, int imageCount) {
            this.markdownKey = markdownKey;
            this.pageMapKey = pageMapKey;
            this.pageCount = pageCount;
            this.blockCount = blockCount;
            this.imageCount = imageCount;
        }

        public String getMarkdownKey() {
            return markdownKey;
        }

        /** 页码映射键（无映射腿＝null） */
        public String getPageMapKey() {
            return pageMapKey;
        }

        public int getPageCount() {
            return pageCount;
        }

        public int getBlockCount() {
            return blockCount;
        }

        public int getImageCount() {
            return imageCount;
        }
    }
}
