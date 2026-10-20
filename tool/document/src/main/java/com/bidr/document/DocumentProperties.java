package com.bidr.document;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: DocumentProperties
 * Description: 文件底座配置（{@code tool.document.*}，纯 yml＋Spring Environment，🔴 零 sys_config、
 * 不依赖 platform 缓存，R-9）。全部键带缺省＝不配即可跑；硬参数缺省值与历史
 * {@code FileMarkdownService} 写死值逐字相同（100MB/50 页/150DPI/20 字每页），配置面放开但
 * 行为基线不变。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Data
@ConfigurationProperties(prefix = "tool.document")
public class DocumentProperties {

    /**
     * transcribe 增强腿选型键（纯 yml，改值要重启）：值＝腿名（如 {@code mineru}）。
     * 空＝不选增强腿走内置（不抛）；配了但实现包不在 classpath＝WARN 后走内置；
     * 配了且在＝增强腿，失败回落内置（三态语义正源，§3.1）
     */
    private String transcriber = "";

    /** 单文件大小上限字节（缺省 100MB，防御超大文件与压缩包解包类资源消耗） */
    private long maxFileSizeBytes = 100L * 1024 * 1024;

    /** 单次解析送多模态模型的扫描页上限（缺省 50，防长扫描件调用成本失控） */
    private int maxVisionPages = 50;

    /** 扫描页渲染 DPI（缺省 150，兼顾识别精度与图片体积） */
    private float renderDpi = 150f;

    /** 单页可提取文本低于该字数即判定为扫描页（缺省 20，正常文字页远不止此量） */
    private int minTextPerPage = 20;

    /** 缺省档（全部硬参数取历史缺省值） */
    public static DocumentProperties defaults() {
        return new DocumentProperties();
    }
}
