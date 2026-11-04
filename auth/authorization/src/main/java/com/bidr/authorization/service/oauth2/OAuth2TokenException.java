package com.bidr.authorization.service.oauth2;

/**
 * Title: OAuth2TokenException
 * Description: token 端点的 OAuth2 标准错误形态载体（T1.2 棒 6）——{@code error} 取 RFC 6749 §5.2
 * 错误码（invalid_client / invalid_grant / unsupported_grant_type / invalid_request），
 * controller 捕获后转 HTTP 400 ＋ {@code {"error":..,"error_description":..}} JSON，
 * 不走框架业务异常的全局形状（消费方按标准 OAuth2 形态解析）。
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class OAuth2TokenException extends RuntimeException {

    private final String error;

    public OAuth2TokenException(String error, String description) {
        super(description);
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
