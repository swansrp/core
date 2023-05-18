package com.bidr.authorization.vo.group;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Title: BindUserReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/17 08:53
 */
@Data
public class BindUserReq {
    @NotNull(message = "用户组id不能为空")
    private Long groupId;

    private String name;

    private Integer dataScope;
}
