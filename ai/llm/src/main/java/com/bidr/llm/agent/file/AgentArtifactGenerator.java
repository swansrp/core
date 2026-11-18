package com.bidr.llm.agent.file;

/**
 * Title: AgentArtifactGenerator
 * Description: agent 会话产物生成端口（llm 协议层，零外围依赖）——把模型产出的 Markdown 正文
 * 转成可下载的 docx/pdf 落对象存储并签发读址。端口缺席＝产物能力不启用
 * （{@code /agents} 不出 {@code artifacts.doc} 能力位，{@code generate_artifact} 工具回
 * 「产物生成未启用」）；具体实现由能力模块（{@code ai/agent-capability}）在
 * gotenberg 转换腿在场时装配（{@code ArtifactService}）。
 * <p>
 * 依赖方向与 {@link AgentFileStore} 同款：llm 只声明端口与协议记录
 * （{@link AgentArtifact}），转换/存储细节（Markdown→docx 渲染、gotenberg、BlobStore
 * 对象键规则）全部收在能力模块，llm 不感知。
 * <p>
 * 失败语义：实现以异常表达失败（非法格式 {@code IllegalArgumentException}、转换/存储失败
 * {@code IllegalStateException} 等），调用方（工具层）统一转人话错误文本，不向模型暴露内部细节。
 *
 * @author Sharp
 * @since 2026/11/13
 */
public interface AgentArtifactGenerator {

    /**
     * 生成会话产物并落对象存储
     *
     * @param sessionId 会话标识（对象键隔离维度）
     * @param markdown  Markdown 正文全文（模型在自身上下文里已持有，由工具参数直传）
     * @param format    产物格式：{@code docx}/{@code pdf}（大小写不敏感；其余值拒绝）
     * @param title     展示用文件名（可空；空则实现按时间戳回落）
     * @return 产物引用（含预签名读址）
     */
    AgentArtifact generate(String sessionId, String markdown, String format, String title);
}
