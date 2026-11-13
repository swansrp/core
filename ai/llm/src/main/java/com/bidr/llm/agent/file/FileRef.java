package com.bidr.llm.agent.file;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: FileRef
 * Description: 会话附件引用（不可变记录）——附件协议的最小定位单元，跨 llm 端口、
 * 事件流与前端渲染共用。字段口径（跨模块契约，不得改名）：
 * <ul>
 *     <li>{@code fileId}：会话内附件标识（存储实现分配，上传响应与事件 payload 的 file_id 同值）。
 *     <b>端口契约（m-r2-6）</b>：字符集固定为 {@code [A-Za-z0-9_-]}、长度 1～128 —— 该形态同时是
 *     {@code read_attachment} 工具入参闸与提示词内联口径，自定义 {@link AgentFileStore} 实现
 *     必须按此生成，否则模型拿到的 file_id 会被剥离/拒绝而永远读不到附件；</li>
 *     <li>{@code name}：原始文件名（展示用，含扩展名）；</li>
 *     <li>{@code size}：字节数；</li>
 *     <li>{@code mime}：内容类型（未知时存储实现回落 octet-stream）；</li>
 *     <li>{@code key}：存储层对象键（{@link AgentFileStore} 实现私有口径，业务不解析；
 *     仅从事件流重建的引用该字段为 null，需内容时经 {@link AgentFileStore#get} 换回完整引用）。</li>
 * </ul>
 *
 * @param fileId 会话内附件标识
 * @param name   原始文件名
 * @param size   字节数
 * @param mime   内容类型
 * @param key    存储对象键（可由实现自解析，业务不依赖）
 * @author Sharp
 * @since 2026/11/13
 */
public record FileRef(String fileId, String name, long size, String mime, String key) {

    /** 事件流 payload（file_id/name/size/mime，snake_case；key 不入事件）——唯一组装口 */
    public Map<String, Object> toEventPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("file_id", fileId);
        payload.put("name", name);
        payload.put("size", size);
        payload.put("mime", mime);
        return payload;
    }

    /**
     * 由事件流 payload 重建引用（key 恒为 null）：file_id 缺失返回 null，
     * 其余字段按缺省空值/0 宽容解析，单条脏事件不拖垮附件清单
     */
    public static FileRef fromEventPayload(Map<?, ?> payload) {
        if (payload == null || payload.get("file_id") == null) {
            return null;
        }
        return new FileRef(String.valueOf(payload.get("file_id")), textOf(payload.get("name")),
                longOf(payload.get("size")), textOf(payload.get("mime")), null);
    }

    private static String textOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longOf(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 脏数据按 0 处理
            }
        }
        return 0L;
    }
}