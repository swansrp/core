package com.bidr.authorization.constants.dict;

import cn.hutool.core.util.EnumUtil;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: DataPermitScopeDict
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/01 00:37
 */
@MetaDict(value = "DATA_PERMIT_SCOPE_DICT", remark = "数据权限作用域字典")
@Getter
@AllArgsConstructor
public enum DataPermitScopeDict implements Dict {
    /**
     * 数据权限作用域字典
     */
    DEPARTMENT(0, "本部门"),
    OWNER(1, "本人"),
    SUBORDINATE(2, "本部门及子部门"),

    ALL(3, "全体部门"),

    OTHER(4, "其他");

    private final Integer value;
    private final String label;

    public static DataPermitScopeDict of(Integer value) {
        return EnumUtil.getBy(DataPermitScopeDict::getValue, value);
    }
}
