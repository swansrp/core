package com.bidr.llm.usage;

import lombok.Data;

/**
 * Title: ModelUsageFacts
 * Description: 一次模型调用的用量事实（M14 棒5 计量表的入参）：框架包装层**确实知道**的那一份，
 * 不做任何计价与汇总——单价是运营政策、会变、各项目不同，按立项口径留在上层算。
 *
 * <p><b>为什么只有这里能拿到</b>：purpose/模型名/key/token/耗时都产生于模型包装层内部，
 * 消费方（会话、摘要、拟标题、范围解析、insight）拿到的是已拼好的 {@code Response}，
 * 事后按模型名反查既对不上当时的 key，也补不回思考 token。</p>
 *
 * <p><b>凭据纪律</b>：本类只带 {@link #keyFingerprint}（sha256 前若干位 hex），
 * <b>绝不携带明文 key</b>；指纹为空＝这一路没配 key（如本地假网关），不编造。</p>
 *
 * <p>建完即交出，接收方（sink）只读——sink 可能把它排进另一个线程的落库队列。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Data
public class ModelUsageFacts {

    /** 调用载体：同步客户端 */
    public static final String VIA_SYNC = "SYNC";
    /** 调用载体：SSE 流式客户端 */
    public static final String VIA_SSE = "SSE";
    /** 调用载体：多模态客户端 */
    public static final String VIA_VISION = "VISION";

    /** 调用载体（SYNC/SSE/VISION）：同一 purpose 走不同客户端的成本口径不同，读数要分得开 */
    private String via;

    /** 用途档（provider 的 purposeType，如 AGENT/VISION/DEFAULT/NODE） */
    private String purposeType;

    /** 本次实际生效的模型 id（provider 现读值，非配置默认值） */
    private String modelName;

    /** key 指纹（sha256 hex 前 16 位）；🔴 明文 key 不进本类、不进库、不进日志 */
    private String keyFingerprint;

    /** 会话标识（有则填：工具循环链路带得出来，直连小请求为 NULL） */
    private String sessionId;

    /** agent 运行时注册键（有则填，同上） */
    private String agentKey;

    /** 发起人（有则填，同上；与装配单 asked_by 同口径） */
    private String askedBy;

    /** 输入 token；网关没回 usage 时为 NULL（NULL＝没拿到，不等于 0） */
    private Integer promptTokens;

    /** 输出 token；同上 */
    private Integer completionTokens;

    /** 思考 token（兼容 completion_tokens_details 内嵌与顶层两种网关口径）；无该字段＝ NULL */
    private Integer reasoningTokens;

    /** 总 token（网关给就有；缺失不自己相加，相加值与网关账单口径可能不一致） */
    private Integer totalTokens;

    /** 本轮模型决定调用几只工具（工具循环轮的形态读数） */
    private Integer toolCount;

    /** 网关 finish_reason 原值（stop/length/tool_calls/…）；缺失为 NULL */
    private String finishReason;

    /** 本次调用耗时（毫秒，从发起到收到可解析响应） */
    private Long costMs;

    /** 调用收口时刻（epoch 毫秒）：异步落库时 create_at 是插入时刻，这一栏才是用量发生时刻 */
    private Long callAtMs;
}
