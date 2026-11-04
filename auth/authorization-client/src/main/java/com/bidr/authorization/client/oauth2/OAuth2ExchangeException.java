package com.bidr.authorization.client.oauth2;

/**
 * Title: OAuth2ExchangeException
 * Description: code 换 token 失败（IdP 400 {error,error_description} / 网络 / 信封形状异常）。
 *
 * <p>迁移自 epc {@code InkHubOAuth2Client.OAuth2ExchangeException}，语义不变：调用方按
 * "换票失败"单一分支处理即可，无需区分 HTTP 拒绝与网络异常。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
public class OAuth2ExchangeException extends RuntimeException {

    public OAuth2ExchangeException(String message) {
        super(message);
    }

    public OAuth2ExchangeException(String message, Throwable cause) {
        super(message, cause);
    }
}
