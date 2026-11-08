package com.bidr.platform.openapi;

import lombok.Data;

/**
 * Title: OpenApiEnvelope
 * Description: 开放口统一响应<b>最小信封</b> DTO（方案B 棒4 §2「core 传输」层）——
 * {@code {status:{code,msg}, payload:T}}，Jackson 反序列化。
 *
 * <p><b>为什么住在 platform 且自带一份</b>：platform 是零 auth 依赖的最底层（依赖方向铁律
 * platform ← authorization-client（凭据）← 场景客户端），不能引用 authorization-api 的共享
 * {@code Envelope}（那会把 auth 类型面拖进基础层）。开放口线格式的消费契约（status.code==0
 * 取 payload）由 {@link OpenApiDataClient} 统一解包钉死，本 DTO 只声明<b>传输关心的两个字段</b>；
 * 线上多出的字段（如 {@code details}、payload 内的附加属性）由 DataClient 的宽容 Jackson
 * （FAIL_ON_UNKNOWN_PROPERTIES=false）静默忽略——服务端加字段不炸消费侧。</p>
 *
 * <p>线格式与 hub 宿主统一 Response 信封（kernel {@code ResponseExceptionHandler} 对
 * com.bidr 包内控制器的全局包裹）同构；非零信封的抛错契约见
 * {@link OpenApiEnvelopeException}。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
@Data
public class OpenApiEnvelope<T> {

    /** 成功码：与服务端统一信封一致（0＝成功） */
    public static final int SUCCESS_CODE = 0;

    private Status status;

    private T payload;

    /** 信封状态段（消费侧最小面：code/msg；details 等线上字段由宽容反序列化吸收） */
    @Data
    public static class Status {

        private Integer code;

        private String msg;
    }
}
