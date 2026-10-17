package com.bidr.kernel.constant.dict.portal.anno;

import java.lang.annotation.*;

/**
 * Title: PortalEditAbleField
 * Description: 表格行内编辑列（edit_able='1'）——原由 sys_portal_column initData 硬编码，现由注解正源。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalEditAbleField {
}
