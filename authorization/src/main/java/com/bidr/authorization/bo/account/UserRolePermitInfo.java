package com.bidr.authorization.bo.account;

import com.bidr.authorization.bo.role.RoleInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: UserRolePermitInfo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/05 10:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserRolePermitInfo extends UserPermitInfo {
    private List<RoleInfo> roleInfoList;
}
