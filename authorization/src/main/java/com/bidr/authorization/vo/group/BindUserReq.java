package com.bidr.authorization.vo.group;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Title: BindUserReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/17 08:53
 */
@Data
public class BindUserReq {
    private Long groupId;

    private List<Long> groupIdList;

    private String name;

    private Integer dataScope;
}
