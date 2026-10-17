package com.bidr.mineru;

import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Title: MineruArtifactCodec
 * Description: MinerU 结果 zip 的公共解码件（两腿共用）。归一自两份参照实现（InkHub 引擎侧
 * 与 pm-hse 业务侧），实测口径：
 * <ul>
 *   <li>zip 条目＝{@code full.md}、{@code <id>_content_list.json}、{@code <id>_content_list_v2.json}、
 *       {@code images/*.jpg}、{@code layout.json}、{@code *_origin.pdf} 等；<b>v2 与中间 json 一律跳过</b>；</li>
 *   <li>markdown 缺失＝失败（两腿都必产 md）；content_list 缺席不判失败（判分类消费方只要 md）；</li>
 *   <li>图片只认扩展名，layout/span 调试 PDF 不收；条目名剥掉打包外层目录归一成 md 引用形态。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/10/17
 */
final class MineruArtifactCodec {

    /** 只认图片扩展名——zip 里还有 *_layout.pdf / *_span.pdf / 中间 json，一律不收 */
    private static final String[] IMAGE_EXTS = {"jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff"};

    private MineruArtifactCodec() {
    }

    /**
     * 解 MinerU 结果 zip 并归一。
     *
     * @param zip    zip 原始字节
     * @param handle 任务标识（taskId/batchId），只用于报错时指认是哪一单
     */
    static MineruArtifact readZip(byte[] zip, String handle) {
        if (zip == null || zip.length == 0) {
            throw new IllegalStateException("MinerU 结果为空: " + handle);
        }
        String markdown = null;
        String contentList = null;
        MineruArtifact artifact = new MineruArtifact();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith("_content_list_v2.json")) {
                    continue;
                }
                if (name.endsWith(".md")) {
                    markdown = readUtf8(zin);
                } else if (name.endsWith("_content_list.json")) {
                    contentList = readUtf8(zin);
                } else if (isImage(name)) {
                    MineruArtifact.MineruImage image = new MineruArtifact.MineruImage();
                    image.setName(productName(name));
                    image.setData(readBytes(zin));
                    artifact.getImages().add(image);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("解析 MinerU zip 失败: " + handle, e);
        }
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalStateException("MinerU 结果缺 markdown: " + handle);
        }
        artifact.setMarkdown(markdown.trim());
        artifact.setContentList(StringUtils.hasText(contentList) ? contentList : null);
        return artifact;
    }

    /** zip 内图片条目：只收图片扩展名，layout/span 等调试 PDF 与中间 json 一律丢弃 */
    private static boolean isImage(String entryName) {
        String lower = entryName.toLowerCase();
        for (String ext : IMAGE_EXTS) {
            if (lower.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /** zip 条目名归一为 markdown 里的引用形态（去掉打包时可能带上的外层目录） */
    private static String productName(String entryName) {
        int idx = entryName.indexOf("images/");
        return idx > 0 ? entryName.substring(idx) : entryName;
    }

    private static String readUtf8(ZipInputStream zin) throws Exception {
        return new String(readBytes(zin), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(ZipInputStream zin) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = zin.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }
}
