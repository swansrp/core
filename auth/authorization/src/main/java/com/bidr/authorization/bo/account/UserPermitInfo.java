package com.bidr.authorization.bo.account;

import com.bidr.authorization.bo.permit.PermitInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: UserPermitInfo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 15:05
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPermitInfo extends UserInfo {
    private List<PermitInfo> menuList;

}
