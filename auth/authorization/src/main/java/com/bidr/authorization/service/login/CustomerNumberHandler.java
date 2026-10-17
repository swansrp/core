package com.bidr.authorization.service.login;

import com.bidr.authorization.dao.entity.AcUser;

/**
 * @author Sharp
 */
public interface CustomerNumberHandler {
    /**
     * 生成customerNumber的方法
     *
     * @param user
     * @return
     */
    String getCustomerNumber(AcUser user);
}
