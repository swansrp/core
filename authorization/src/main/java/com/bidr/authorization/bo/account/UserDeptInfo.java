package com.bidr.authorization.bo.account;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: UserDeptInfo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 18:31
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserDeptInfo extends AcUser {
    private AcDept acDept;
}
