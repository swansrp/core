package com.bidr.authorization.bo.group;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import lombok.Data;

/**
 * Title: AcUserGroupWithGroup
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/10 11:27
 */
@Data
public class AcUserGroupWithUser extends AcUserGroup {
    private AcUser user;
}
