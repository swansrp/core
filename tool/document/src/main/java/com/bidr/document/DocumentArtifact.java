package com.bidr.document;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: DocumentArtifact
 * Description: 文件加工产物契约＝<b>正文＋路线留痕</b>（{@link ArtifactStep} 槽的落形结果）。
 * 出参只用纯 String——产物字节落库归业务/上层（本模块不耦合对象存储）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Getter
@RequiredArgsConstructor
public class DocumentArtifact {

    /** Markdown 正文（非 null；可能为空串——空正文也带留痕出门） */
    private final String markdown;

    /** 路线留痕：哪条 convert 腿×哪条 transcribe 腿／内置直读＋是否降级无页 */
    private final RouteTrace trace;
}
