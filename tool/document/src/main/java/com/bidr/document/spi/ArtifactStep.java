package com.bidr.document.spi;

import com.bidr.document.DocumentArtifact;
import com.bidr.document.RouteTrace;

/**
 * Title: ArtifactStep
 * Description: 文件加工流水线的第 5 槽——<b>产物落形</b>：把正文与路线留痕组装成最终产物契约
 * {@link DocumentArtifact}。内置实现＝{@code builtin.DefaultArtifactStep}（markdown＋trace 直包装）。
 * <p>
 * 业务要 richer 产物（附产物字节句柄、扩展字段）＝注册自己的 {@link ArtifactStep}；
 * 🔴 路线留痕是产物契约的<b>显式字段</b>——降级腿不许伪装正常，「哪条 convert 腿×哪条
 * transcribe 腿／内置直读＋是否降级无页」必须原样带到最终产物上（判据⑤的落点）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public interface ArtifactStep {

    /**
     * 组装产物。
     *
     * @param markdown 最终正文（已过 Enrich 槽）
     * @param trace    路线留痕（convert×transcribe 走法＋降级标记）
     */
    DocumentArtifact shape(String markdown, RouteTrace trace);
}
