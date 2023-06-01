package com.bidr.authorization.vo.account;

import lombok.Data;

import java.util.List;

/**
 * Title: AccountReq
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 15:14
 */
@Data
public class AccountReq {
    private List<String> deptIdList;
    private String name;
}
