package com.bidr.authorization.constants.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: RoleTypeDict
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/01 00:50
 */
@MetaDict(value = "ROLE_TYPE_DICT", remark = "角色类型字典")
@Getter
@AllArgsConstructor
public enum RoleTypeDict implements Dict {
    /**
     * 是非字典表
     */
    MENU(0, "功能角色"),
    DATA(1, "数据角色");

    private final Integer value;
    private final String label;
}
