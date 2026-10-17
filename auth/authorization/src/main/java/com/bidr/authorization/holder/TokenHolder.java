package com.bidr.authorization.holder;

import com.bidr.authorization.bo.token.TokenInfo;

/**
 * Title: TokenHolder
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 09:36
 */
public class TokenHolder {

    private static final ThreadLocal<TokenInfo> TOKEN_HOLDER = new ThreadLocal<>();

    private TokenHolder() {
    }

    public static void set(TokenInfo s) {
        TOKEN_HOLDER.set(s);
    }

    public static TokenInfo get() {
        return TOKEN_HOLDER.get();
    }

    public static void remove() {
        if (TOKEN_HOLDER.get() != null) {
            TOKEN_HOLDER.remove();
        }
    }
}
