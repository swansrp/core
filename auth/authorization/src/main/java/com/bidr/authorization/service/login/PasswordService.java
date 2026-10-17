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
    String resetPassword(String customerNumber);

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

    /**
     * 发送更换密码邮件
     *
     * @param userId 用户名
     * @param email  邮箱
     */
    void sendChangePasswordEmail(String userId, String email);

    /**
     * 通过手机号修改密码
     *
     * @param phoneNumber
     * @param password
     */
    void changePasswordByMsgCode(String phoneNumber, String password);

    /**
     * 发起修改密码邮件通知
     *
     * @param userId 用户名
     * @param email  邮箱
     */
    void emailChangeReq(String userId, String email);

    /**
     * 修改用户密码
     *
     * @param userId   用户名
     * @param password 密码
     */
    void changePassword(String userId, String password);

}
