package com.bidr.llm.agent.file;

/**
 * Title: AgentFileStore
 * Description: agent 会话附件存储端口（llm 协议层，零外围依赖）——上传端点只认本接口，
 * 具体落盘由能力模块（如 {@code ai/agent-capability} 的 BlobStore 实现）装配；
 * 端口缺席＝附件能力不启用（{@code /agents} 不出 {@code files.upload} 能力位，上传端点报未启用）。
 * <p>
 * 归属约定：{@link #get(String, String)}／{@link #read(FileRef)} 的调用方已由会话层校验
 * 「操作人＝会话发起人」；实现侧仍须以 sessionId 前缀隔离对象键，避免跨会话猜 fileId 直读。
 * <p>
 * 事件流只携 file_id/name/size/mime（见 {@code AgentEvent#FILE_ATTACHED}），不携 key——
 * 从事件重建的 {@link FileRef#key()} 为 null，属正常形态。
 * <p>
 * <b>fileId 字符集契约（m-r2-6）</b>：实现分配/接受的 {@code fileId} 必须匹配
 * {@code [A-Za-z0-9_-]{1,128}}。该形态是会话受限工具 {@code read_attachment} 的入参闸，
 * 也是提示词内联与事件重建的共同口径；实现若使用带点/冒号/花括号等字符的 id，
 * 模型会收到被剥离后的 file_id（或工具直接按不存在拒绝），表现为"附件永远读不到"。
 * 当前实现（{@code BlobStoreAgentFileStore}）生成 32 位无连字符 UUID，满足契约。
 *
 * @author Sharp
 * @since 2026/11/13
 */
public interface AgentFileStore {

    /**
     * 写入一份会话附件
     *
     * @param sessionId 会话标识（对象键隔离维度）
     * @param filename  原始文件名（实现从中取扩展名；可含路径，实现负责清洗）
     * @param mime      内容类型（空则实现按扩展名推断）
     * @param bytes     文件字节
     * @return 完整附件引用（fileId/name/size/mime/key）；fileId 必须满足 {@code [A-Za-z0-9_-]{1,128}}
     */
    FileRef put(String sessionId, String filename, String mime, byte[] bytes);

    /**
     * 按会话内附件标识取回完整引用
     *
     * @param sessionId 会话标识
     * @param fileId    附件标识
     * @return 附件引用；不存在返回 null
     */
    FileRef get(String sessionId, String fileId);

    /** 读回附件字节（ref.key 须非空，即来自 {@link #put} 或 {@link #get} 的完整引用） */
    byte[] read(FileRef ref);

    /**
     * 签发可读 URL（会话受限工具 {@code read_attachment} 与前端预览共用）
     *
     * @param ref        附件引用（key 须非空）
     * @param ttlSeconds 期望有效期（秒）；实现按自身存储后端能力取近似值
     */
    String presignedUrl(FileRef ref, long ttlSeconds);

    /** 删除附件（默认 no-op：存储实现不提供删除能力时静默跳过） */
    default void delete(FileRef ref) {
    }
}