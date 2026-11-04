package com.bidr.authorization.envelope;

import lombok.Data;

/**
 * 统一响应信封（消费侧解包契约）——{@code {status:{code,msg,details}, payload:T}}。
 *
 * <p><b>为什么住在 api</b>（2026-11-04 对账单 #27 裁定）：hub 宿主（kernel
 * {@code ResponseExceptionHandler} ＋ {@code @ControllerAdvice}）对 {@code com.bidr} 包内控制器
 * 统一把返回值包进 kernel 的 {@code Response} 信封；而消费方分散在多个模块——曾经
 * {@code authorization-client.HttpAuthzCheckApi} 与 {@code identity-mirror.IdentitySnapshotClient}
 * 各自内联一份同名同构 DTO（当时因"两模块互不依赖"的纪律），加上 {@code agent-runtime} 的
 * 第三份，共三拷贝。三份手抄的字段名一旦与服务端漂移，消费侧是<b>静默解不出</b>（不报错，
 * 得 null/空集合）——{@code /open-api/v1/projects} 的 teams.members 恒空（#38）即同型故障的
 * 现实样本。故按既有裁定「类型契约件归 api」把信封收敛到这里，client 与 mirror 共用一份；
 * {@code agent-runtime} 的 Envelope 暂保留（用户 2026-11-04 裁：auth 统一、runtime 暂不动）。</p>
 *
 * <p><b>防漂移</b>：{@code EnvelopeContractTest} 用 kernel {@code Response} 的<b>真序列化</b>
 * （而非手写 JSON 字面量）反向解包断言——服务端信封形态异动会立即在该测试变红。</p>
 *
 * @author sharp
 * @since 2026-11-04
 */
@Data
public class Envelope<T> {

    /** 成功码：kernel {@code ErrCodeSys.SUCCESS}＝0，与服务端 Response 一致 */
    public static final int SUCCESS_CODE = 0;

    private Status status;

    private T payload;

    /**
     * 断言成功信封并保证 payload 非空。
     *
     * @param face 出错文案的面向名（如 {@code /authz/check}、{@code 身份快照端点}）——
     *             各调用方保留原有文案前缀，不因共用而改写错误信息
     * @throws IllegalStateException 无 status／code 非 0／payload 为空
     */
    public void assertSuccess(String face) {
        boolean ok = status != null && status.getCode() != null && SUCCESS_CODE == status.getCode();
        if (!ok) {
            String detail = status == null ? "无 status"
                    : (status.getCode() == null ? "无 code" : status.getCode() + "/" + status.getMsg());
            throw new IllegalStateException(face + " 返回非成功信封: " + detail);
        }
        if (payload == null) {
            throw new IllegalStateException(face + " 信封 payload 为空");
        }
    }

    /** 信封状态段（与服务端 kernel Response.status 同形） */
    @Data
    public static class Status {
        private Integer code;
        private String msg;
        private String details;
    }
}