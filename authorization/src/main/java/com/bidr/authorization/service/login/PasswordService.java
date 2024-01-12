package com.bidr.authorization.service.login;

import com.bidr.authorization.vo.login.pwd.ChangePasswordReq;
import com.bidr.authorization.vo.login.pwd.InitPasswordReq;

/**
 * Title: PasswordService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/11 08:45
 */
public interface PasswordService {

    /**
     * 重置账号密码
     *
     * @param customerNumber 账户号
     */
    void resetPassword(String customerNumber);

    /**
     * 初始化密码
     *
     * @param req 密码
     */
    void initPassword(InitPasswordReq req);

    /**
     * 变更账号密码
     *
     * @param req 更新密码
     */
    void changePassword(ChangePasswordReq req);

}
