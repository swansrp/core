package com.bidr.parse;

import com.bidr.blob.BlobStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Title: PageImageRenderer
 * Description: 页图渲染（M17 棒 3 自 InkHub {@code PageImageRenderer} 原样迁入，D-7）：
 * PDF 逐页渲染 PNG 存 {@code thumb/{sha256}/p{n}.png}（D-11 裁剪键的成员——请求里减掉
 * {@code pageImage} 时本件整段跳过）。
 *
 * <p><b>为什么要页图</b>：视觉工具与"引用回看原文"都要能直接给模型/用户一张页图
 * （坐标 bbox 只能指向区域，页图才是证据本体）。同时它也是解析结果的人工抽检手段——
 * 页数、版面一眼可验。</p>
 *
 * <p><b>失败不阻断解析</b>：页图是辅助产物，渲染单页失败只告警并跳过（返回实际成功页数），
 * 不让"某个畸形页"把整条解析流水线拖失败。非 PDF（docx 等）直接跳过由调用方判断扩展名。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
public class PageImageRenderer {

    /** 渲染分辨率：120 DPI 兼顾可读性与体积（A4 ≈ 992×1403） */
    private static final float DPI = 120f;

    /** 单文档渲染页数上限：防超大 PDF 把跑批线程与对象存储拖垮 */
    private static final int MAX_PAGES = 300;

    private final BlobStore storage;

    public PageImageRenderer(BlobStore storage) {
        this.storage = storage;
    }

    /**
     * 渲染 PDF 逐页页图。
     *
     * @param sha256 内容哈希（页图 key 的寻址依据，与原件同源）
     * @param pdf    原件字节
     * @return 实际渲染成功的页数；非 PDF 或解析失败返回 0
     */
    public int render(String sha256, byte[] pdf) {
        if (pdf == null || pdf.length == 0 || !looksLikePdf(pdf)) {
            return 0;
        }
        int rendered = 0;
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            int limit = Math.min(pages, MAX_PAGES);
            if (pages > MAX_PAGES) {
                log.warn("PDF 页数超过渲染上限，只渲染前 {} 页: sha={} pages={}", MAX_PAGES, sha256, pages);
            }
            for (int page = 0; page < limit; page++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(page, DPI);
                    storage.put(BlobStore.thumbKey(sha256, page + 1), toPng(image),
                            BlobStore.contentTypeOf("p.png"));
                    rendered++;
                } catch (Exception e) {
                    log.warn("页图渲染失败（跳过该页）: sha={} page={} {}", sha256, page + 1, e.getMessage());
                }
            }
            log.info("页图渲染完成: sha={} 成功={}/{}", sha256, rendered, pages);
        } catch (Exception e) {
            log.warn("PDF 打开失败，跳过页图渲染: sha={} {}", sha256, e.getMessage());
            return 0;
        }
        return rendered;
    }

    private static byte[] toPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** 按魔数判断 PDF（不看扩展名：上传件可能名实不符，扩展名只用于展示）。流程断点判据共用 */
    public static boolean looksLikePdf(byte[] bytes) {
        return bytes != null && bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F';
    }
}
