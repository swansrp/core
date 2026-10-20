package com.bidr.document.builtin;

import com.bidr.document.DocumentProperties;
import com.bidr.document.VisionEndpoint;
import com.bidr.document.converter.DocxMarkdownConverter;
import com.bidr.document.converter.ExcelMarkdownConverter;
import com.bidr.document.converter.HtmlMarkdownConverter;
import com.bidr.document.converter.PptxMarkdownConverter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Locale;

/**
 * Title: BuiltInDispatcher
 * Description: 内置腿集＝历史 {@code FileMarkdownService.parseBytes} 的格式分发<b>逐字迁入</b>
 * （扩展名大小写归一、分支顺序、报错口径全部不变）：文本直读／html 结构转／POI 三件＋旧格式
 * 两条／PDFBox 逐页＋扫描页走多模态／图片直送多模态／未知扩展名前 8KB 探 NUL。
 * <p>
 * {@link #isOfficeExt(String)} 是<b>分派副产物</b>（这些扩展名在分发表里归 POI/旧格式腿），
 * 仅供路线留痕判定「引用降级无页」，不是能力判定面——能力查询走 {@code FileRouter.isProcessable}
 * （逐腿问认领，受理收口归业务侧自己的闸）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@RequiredArgsConstructor
public class BuiltInDispatcher {

    private final DocumentProperties properties;

    private final VisionTranscriber visionTranscriber;

    /**
     * 按扩展名分派内置腿（switch 分支与历史 FileMarkdownService 逐字对齐）。
     */
    public String dispatch(byte[] bytes, String filename, VisionEndpoint vision) throws IOException {
        String ext = extensionOf(filename);
        switch (ext) {
            case "md":
            case "markdown":
            case "txt":
            case "log":
            case "csv":
            case "tsv":
            case "json":
            case "xml":
            case "yml":
            case "yaml":
            case "properties":
            case "sql":
                return TextDecoder.decode(bytes);
            case "html":
            case "htm":
                return HtmlMarkdownConverter.convert(TextDecoder.decode(bytes));
            case "docx":
                return DocxMarkdownConverter.convert(bytes);
            case "doc":
                return LegacyOfficeExtractor.doc(bytes);
            case "xlsx":
            case "xls":
                return ExcelMarkdownConverter.convert(bytes);
            case "pptx":
                return PptxMarkdownConverter.convert(bytes);
            case "ppt":
                return LegacyOfficeExtractor.ppt(bytes);
            case "pdf":
                return new PdfTextExtractor(properties, visionTranscriber).extract(bytes, vision);
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "webp":
            case "bmp":
                return visionTranscriber.transcribe(bytes, mimeTypeOf(ext), vision);
            default:
                return parseUnknown(bytes, filename);
        }
    }

    /**
     * 内置集对任意文件名都有认领腿：已知扩展名归对应分派腿，未知扩展名归 NUL 探测腿
     * （内容级二进制拒绝在加工时发生）——分派副产物，能力面不收窄。
     */
    public boolean claims(String filename) {
        return true;
    }

    /**
     * 该扩展名是否走内置 office 分派（POI 三件＋旧格式两条）——降级无页的判定原料。
     */
    public boolean isOfficeExt(String filename) {
        switch (extensionOf(filename)) {
            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
                return true;
            default:
                return false;
        }
    }

    /**
     * 未知扩展名：按二进制特征判断，非二进制尝试当文本解码，二进制直接拒绝（历史口径不变）。
     */
    private String parseUnknown(byte[] bytes, String filename) {
        if (BinaryProbe.looksBinary(bytes)) {
            throw new IllegalArgumentException("暂不支持的文件类型：" + filename);
        }
        return TextDecoder.decode(bytes);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String mimeTypeOf(String ext) {
        switch (ext) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            default:
                return "application/octet-stream";
        }
    }
}
