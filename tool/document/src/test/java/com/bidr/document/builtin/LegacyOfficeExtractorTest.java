package com.bidr.document.builtin;

import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: LegacyOfficeExtractorTest
 * Description: POI 5.x 兼容钉桩：旧格式 PPT 腿在 5.0 删除 {@code PowerPointExtractor} 后改用
 * {@code HSLFSlideShow + SlideShowExtractor}，此处用内存生成的最小 .ppt 验证正文仍可取回
 * （防止只做版本对齐、不验提取面导致的静默失效）。
 *
 * @author Sharp
 * @since 2026/11/13
 */
class LegacyOfficeExtractorTest {

    /** 内存生成 .ppt（一个文本框）→ 提取正文含该文本 */
    @Test
    void pptTextExtractionWorksOnPoiFive() throws Exception {
        byte[] bytes;
        try (HSLFSlideShow slideShow = new HSLFSlideShow()) {
            HSLFSlide slide = slideShow.createSlide();
            HSLFTextBox box = slide.createTextBox();
            box.setText("旧格式幻灯片文本");
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                slideShow.write(out);
                bytes = out.toByteArray();
            }
        }
        String text = LegacyOfficeExtractor.ppt(bytes);
        assertTrue(text.contains("旧格式幻灯片文本"), "POI5 旧格式 PPT 腿必须仍能取到正文，实际=" + text);
    }
}