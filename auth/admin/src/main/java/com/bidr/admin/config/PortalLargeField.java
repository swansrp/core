package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalLargeField
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>
 * 声明该列在详情/新增/编辑弹框中的布局尺寸（sys_portal_column.detail_size / add_size / edit_size）。
 * 长文本列请与 {@link PortalTextAreaField} 叠用（本注解只管尺寸，不改字段类型）。
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalLargeField {

    int size() default 2;
}
