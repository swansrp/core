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

    /**
     * 渲染预算（2026-10-22 用户拍板「慢慢转圈可以接受、失败不能接受」）：
     * 底座 120s ＋ 每页 2s（扫描件单页渲染＋PNG 编码＋上传的实测上界余量），随页数伸缩。
     */
    private static final long RENDER_WATCHDOG_BASE_MS = 120_000L;

    private static final long RENDER_WATCHDOG_PER_PAGE_MS = 2_000L;

    /** 渲染分辨率：120 DPI 兼顾可读性与体积（A4 ≈ 992×1403） */
    private static final float DPI = 120f;

    /** 单文档渲染页数上限：防超大 PDF 把跑批线程与对象存储拖垮 */
    private static final int MAX_PAGES = 300;

    private final BlobStore storage;

    public PageImageRenderer(BlobStore storage) {
        this.storage = storage;
    }

    /**
     * 渲染 PDF 逐页页图（看门狗＋动态预算）。
     *
     * <p>🔴 看门狗防的是「单次渲染永不返回」——任务线程被无限占用＝任务行 RUNNING 僵尸化
     * （不抛异常，切面无法计数）。预算随页数伸缩（2026-10-22 用户拍板：慢可接受、失败不可）：
     * 超预算时任务链先行（快照按<b>已渲染页数</b>上报），<b>渲染线程不取消</b>——继续把剩余页
     * 渲染上传完，thumb 对象迟到补齐（内容寻址，消费方按需现取，迟到无损）。</p>
     *
     * @param sha256 内容哈希（页图 key 的寻址依据，与原件同源）
     * @param pdf    原件字节
     * @return 提交时已确认渲染成功的页数；非 PDF 或打不开返回 0
     */
    public int render(String sha256, byte[] pdf) {
        if (pdf == null || pdf.length == 0 || !looksLikePdf(pdf)) {
            return 0;
        }
        // 页数在主线程读出（加载＝纯结构解析，不触 AWT 字体环境；真正会挂起的渲染在守护线程），
        // 预算据此伸缩；加载好的文档整体移交给守护线程（本线程不再触碰，关闭归守护线程）
        final PDDocument document;
        try {
            document = PDDocument.load(pdf);
        } catch (Exception e) {
            log.warn("PDF 打开失败，跳过页图渲染: sha={} {}", sha256, e.getMessage());
            return 0;
        }
        final int pages = document.getNumberOfPages();
        final long budgetMs = RENDER_WATCHDOG_BASE_MS + RENDER_WATCHDOG_PER_PAGE_MS * pages;
        java.util.concurrent.atomic.AtomicInteger rendered = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.Future<?> future = java.util.concurrent.Executors
                .newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "page-image-render-" + sha256.substring(0, 8));
                    t.setDaemon(true);
                    return t;
                }).submit(() -> renderInner(sha256, document, pages, rendered));
        try {
            future.get(budgetMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("页图渲染超预算（{}ms / {} 页），任务链继续；渲染线程不取消、剩余页后台补齐: sha={} 已完成={}",
                    budgetMs, pages, sha256, rendered.get());
        } catch (Exception e) {
            log.warn("页图渲染异常（跳过）: sha={} {}", sha256, e.getMessage());
        }
        return rendered.get();
    }

    /** 渲染体（看门狗线程内执行；文档由主线程移交，本线程负责关闭） */
    private void renderInner(String sha256, PDDocument document, int pages,
            java.util.concurrent.atomic.AtomicInteger rendered) {
        try {
            PDFRenderer renderer = new PDFRenderer(document);
            int limit = Math.min(pages, MAX_PAGES);
            if (pages > MAX_PAGES) {
                log.warn("PDF 页数超过渲染上限，只渲染前 {} 页: sha={} pages={}", MAX_PAGES, sha256, pages);
            }
            for (int page = 0; page < limit; page++) {
                try {
                    BufferedImage image = renderer.renderImageWithDPI(page, DPI);
                    storage.put(BlobStore.thumbKey(sha256, page + 1), toPng(image),
                            BlobStore.contentTypeOf("p.png"));
                    rendered.incrementAndGet();
                } catch (Exception e) {
                    log.warn("页图渲染失败（跳过该页）: sha={} page={} {}", sha256, page + 1, e.getMessage());
                }
            }
            log.info("页图渲染完成: sha={} 成功={}/{}", sha256, rendered.get(), pages);
        } catch (Exception e) {
            log.warn("页图渲染异常（跳过）: sha={} {}", sha256, e.getMessage());
        } finally {
            try {
                document.close();
            } catch (Exception ignore) {
                // 关闭失败不影响结果（堆内临时对象）
            }
        }
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
