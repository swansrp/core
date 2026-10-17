package com.bidr.authorization.vo.group;

import com.bidr.kernel.vo.bind.BindReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: BindUserGroupDataScopeReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/06 14:41
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BindGroupUserDataScopeReq extends BindReq {
    private Integer dataScope;
}
