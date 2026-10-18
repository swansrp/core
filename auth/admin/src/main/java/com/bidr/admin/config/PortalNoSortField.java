package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalNoSortField
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>
 * 显式声明该列不参与表格排序（sys_portal_column.sort_able=0）。
 * 扫描默认列可排序（与建表默认一致），只有语义上排序无意义/不该排序的列才挂本注解。
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalNoSortField {
}
