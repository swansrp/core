package com.bidr.authorization.bo.account;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
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
    private Map<String, Object> extraData = new HashMap<>(0);
}
