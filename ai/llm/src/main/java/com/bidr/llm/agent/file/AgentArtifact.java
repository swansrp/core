package com.bidr.llm.agent.file;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: AgentArtifact
 * Description: 会话产物引用（不可变记录）——agent 生成产物（P2a）的协议最小单元，
 * 跨 {@link AgentArtifactGenerator} 端口、{@code generate_artifact} 工具与事件流共用。
 * 字段口径（跨模块契约，不得改名）：
 * <ul>
 *     <li>{@code fileId}：会话内产物标识（实现分配，须满足 FileRef 同款字符集契约
 *     {@code [A-Za-z0-9_-]{1,128}}）；</li>
 *     <li>{@code name}：展示用文件名（含扩展名，如 {@code 监理日志-2026-11-13.docx}）；</li>
 *     <li>{@code size}：字节数；</li>
 *     <li>{@code mime}：内容类型（docx/pdf 固定 MIME，浏览器按它触发下载）；</li>
 *     <li>{@code format}：产物格式小写（{@code docx}/{@code pdf}）；</li>
 *     <li>{@code url}：生成时点的预签名读址（约 1 小时有效）——下载卡片的 href 数据源，
 *     过期后由模型重新 generate_artifact 出新件（不把签名 URL 当永久地址存，与附件口径一致）。</li>
 * </ul>
 *
 * @param fileId 会话内产物标识
 * @param name   展示用文件名
 * @param size   字节数
 * @param mime   内容类型
 * @param format 产物格式（docx/pdf，小写）
 * @param url    预签名下载地址（生成时点签名，非永久）
 * @author Sharp
 * @since 2026/11/13
 */
public record AgentArtifact(String fileId, String name, long size, String mime, String format, String url) {

    /** 事件流 payload（file_id/name/size/mime/format/url，snake_case）——唯一组装口 */
    public Map<String, Object> toEventPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file_id", fileId);
        payload.put("name", name);
        payload.put("size", size);
        payload.put("mime", mime);
        payload.put("format", format);
        payload.put("url", url);
        return payload;
    }
}
