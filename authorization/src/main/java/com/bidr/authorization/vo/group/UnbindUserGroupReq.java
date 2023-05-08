package com.bidr.authorization.vo.group;

import lombok.Data;

/**
 * Title: UnbindUserGroupReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:26
 */
@Data
public class UnbindUserGroupReq {
    private Object userId;
    private Object groupId;
}
