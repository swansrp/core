package com.bidr.kernel.constant.param;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Title: MetaParam
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 14:00
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MetaParam {
    /**
     * 参数分组名（管理页按模块归类展示）；留空＝由枚举类名去掉 Param 后缀推导
     */
    String value() default "";
}
