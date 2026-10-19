package com.bidr.kernel.config.anno.portal;

import java.lang.annotation.*;

/**
 * Title: PortalDisableField
 * Description: 列配置置灰（enable='0'）——扫描建列时标记该属性为技术性/只读字段，
 * 保留列定义但默认不出现在可见列集（原由 sys_portal_column initData 硬编码，现由注解正源）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalDisableField {
}
