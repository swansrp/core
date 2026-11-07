package com.bidr.platform.openapi;

import org.springframework.http.HttpHeaders;

/**
 * Title: OpenApiAuthProvider
 * Description: 开放口<b>凭据注入面</b>（方案B 棒4 §2「core 凭据」层接口）——场景客户端出发前
 * 把机器凭据写进请求头，{@link OpenApiDataClient} 传输全程不感知、不改动凭据内容。
 *
 * <p><b>x-api-key 语义由实现层定</b>：本接口只约定「往 headers 写凭据」这个动作，头名与值
 * 的编码口径归实现层。既有实现形态＝provider 侧 {@code AuthPartner} 验签口径
 * {@code x-api-key: Base64(appKey:appSecret)}（kernel Base64Util urlSafe 编码＋双解码兜底），
 * 由 authorization-client 的 {@code PartnerCredentialProvider}（机器凭据腿，≠Bearer JWT——
 * 用户身份腿走 OAuth2 换票）落地。platform 零 auth 依赖，头名以本接口常量自持、值语义不进
 * 基础层。</p>
 *
 * <p>常量对齐 authorization-api {@code RequestConst.X_API_KEY}（同值 {@code "x-api-key"}）；
 * platform 不引 auth 模块，故字面量在本包单点声明——若头名变更须两处同步。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public interface OpenApiAuthProvider {

    /** 机器凭据头名（值＝Base64(appKey:appSecret) 由实现层定；对齐 authorization-api RequestConst.X_API_KEY） */
    String X_API_KEY_HEADER = "x-api-key";

    /**
     * 把凭据写入请求头（实现层应只 set 自己的头，不动/不清除既有头）。
     *
     * @param headers 待发出的请求头
     */
    void apply(HttpHeaders headers);
}
