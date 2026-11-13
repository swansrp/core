package com.bidr.document.builtin;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.sl.extractor.SlideShowExtractor;

/**
 * Title: LegacyOfficeExtractor
 * Description: 内置旧格式 office 腿＝历史 {@code FileMarkdownService.extractLegacyDoc}/
 * {@code extractLegacyPpt} 逐字迁入：POI extractor 纯文本提取，结构丢失（降级档，RouteTrace
 * 的 degradedNoPage 会说话）。
 * <p>
 * POI 5.x 兼容：{@code hslf.extractor.PowerPointExtractor} 在 5.0 已删除，旧 PPT 腿改用
 * {@link HSLFSlideShow} + {@link SlideShowExtractor}（同一 HSLF 文本提取面，逐页正文口径不变）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public final class LegacyOfficeExtractor {

    private LegacyOfficeExtractor() {
    }

    public static String doc(byte[] bytes) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(fs)) {
            return extractor.getText().trim();
        }
    }

    public static String ppt(byte[] bytes) throws IOException {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(new ByteArrayInputStream(bytes));
             SlideShowExtractor<?, ?> extractor = new SlideShowExtractor<>(slideShow)) {
            return extractor.getText().trim();
        }
    }
}
