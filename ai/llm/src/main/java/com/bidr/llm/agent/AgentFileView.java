package com.bidr.llm.agent;

import com.bidr.llm.agent.file.FileRef;

/**
 * Title: AgentFileView
 * Description: 会话附件端点的 HTTP 视图（m-r2-7）——只暴露前端需要的四个字段，
 * <b>不回传存储对象键 {@code key}</b>（实现私有：含归属段/会话段/fileId，前端也无消费点）。
 * 事件流与内部存储链路仍使用完整 {@link FileRef}（侧车/读取依赖 key），只有 HTTP 响应收窄。
 *
 * @param fileId 会话内附件标识（read_attachment 入参）
 * @param name   原始文件名
 * @param size   字节数
 * @param mime   内容类型
 * @author Sharp
 * @since 2026/11/13
 */
public record AgentFileView(String fileId, String name, long size, String mime) {

    /** 由完整引用收窄为 HTTP 视图（key 不外泄） */
    public static AgentFileView of(FileRef ref) {
        if (ref == null) {
            return null;
        }
        return new AgentFileView(ref.fileId(), ref.name(), ref.size(), ref.mime());
    }
}