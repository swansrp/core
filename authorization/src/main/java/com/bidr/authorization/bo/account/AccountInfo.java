package com.bidr.authorization.bo.account;

import com.bidr.authorization.bo.role.RoleInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AccountInfo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AccountInfo extends UserInfo {
    private String token;
    private String clientType;
    private Map<Long, RoleInfo> role;
    /**
     * 归属租户 id 集合（显式字段，非 extraData）；null＝老 token/旁路形态无此档
     */
    private List<Long> tenantIds;
    private Map<String, Object> extraData = new HashMap<>(0);
}
