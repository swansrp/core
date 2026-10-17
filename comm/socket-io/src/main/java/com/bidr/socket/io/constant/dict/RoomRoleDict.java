package com.bidr.socket.io.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: RoomRoleDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/31 10:59
 */

@Getter
@RequiredArgsConstructor
@MetaDict(value = "ROOM_ROLE_DICT", remark = "聊天室内角色")
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
public enum RoomRoleDict implements Dict {

    OWNER("0", "所有者"),
    ADMIN("1", "管理员"),
    MEMBER("2", "成员");

    private final String value;
    private final String label;
}
