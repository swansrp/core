/**
 * Copyright © 2018 SRC-TJ Service TG. All rights reserved.
 *
 * @Package: com.srct.service.annotation
 * @author: xu1223.zhang
 * @since: 2018-08-02 16:44
 */
package com.bidr.authorization.annotation.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Auth {
    /**
     * 生效的鉴权角色集。<b>空（缺省）＝登录默认</b>——由客户端运行时
     * {@code AuthInterceptor} 归一化为 {@code AuthLogin}（与上提前的
     * {@code default AuthLogin.class} 对外行为等价）。
     * <p>T2.3c 上提适配：AuthLogin 住 client（运行时组件），api 禁依赖 client
     * （dep-direction 断言①），故默认值由 {@code AuthLogin.class} 改为空数组，
     * 「裸 @Auth＝须登录」语义由拦截器侧 effectiveRoles 归一兜住。</p>
     */
    Class<? extends AuthRole>[] value() default {};

    String perms() default "";

    String extraData() default "";
}
