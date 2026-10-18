package com.bidr.admin.config;

import java.lang.annotation.*;

/**
 * Title: PortalImportField
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>
 * 标记 VO 字段为 Portal 导入模板列：该字段存在即声明本表格支持导入（sys_portal.import_able=1）。
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface PortalImportField {
}
