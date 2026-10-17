package com.bidr.authorization.constants.dict;

import cn.hutool.core.util.EnumUtil;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ResourceSubjectTypeDict
 * Description: 资源授权主体类型——ac_resource_perm.subject_type 的取值
 * <p>
 * 每一个主体类型对应一个 {@code PermSubjectResolver} 实现，新增维度只需新增一个解析器，
 * 不必改动资源权限过滤的核心逻辑。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@MetaDict(value = "RESOURCE_SUBJECT_TYPE_DICT", remark = "资源授权主体类型字典")
@Getter
@AllArgsConstructor
public enum ResourceSubjectTypeDict implements Dict {
    /**
     * 资源授权主体类型
     */
    ROLE(0, "角色"),
    USER(1, "用户"),
    GROUP(2, "用户组"),
    DEPT(3, "部门");

    private final Integer value;
    private final String label;

    public static ResourceSubjectTypeDict of(Integer value) {
        return EnumUtil.getBy(ResourceSubjectTypeDict::getValue, value);
    }
}
