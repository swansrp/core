package com.bidr.authorization.vo.group;

import lombok.Data;

import java.util.List;

/**
 * Title: BindUserGroupReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 18:14
 */
@Data
public class BindUserGroupReq {
    private List<Object> userId;
    private Object groupId;
}
