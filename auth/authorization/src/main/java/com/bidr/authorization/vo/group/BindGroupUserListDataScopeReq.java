package com.bidr.authorization.vo.group;

import com.bidr.kernel.vo.bind.BindListReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: BindGroupUserListDataScopeReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/06 14:45
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BindGroupUserListDataScopeReq extends BindListReq {
    private Integer dataScope;
}
