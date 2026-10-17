package com.bidr.admin.manage.user.vo;

import com.bidr.authorization.vo.admin.UserRes;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: UserAdminRes
 * Description: Copyright: Copyright (c) 2023
 * <p>
 * 🔴 禁止在本类重复声明 UserRes 已有字段：PortalConfigService 字段扫描按"子类优先"排序
 * （ReflectionUtil.getFields），重复声明会把该列整体前移，直接打乱用户管理页的列顺序。
 * Portal 列配置注解一律写在 UserRes 的字段声明上。
 *
 * @author Sharp
 * @since 2023-12-28 21:20
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserAdminRes extends UserRes {
}
