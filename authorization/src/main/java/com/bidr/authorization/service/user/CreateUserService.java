package com.bidr.authorization.service.user;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;

/**
 * Title: CreateUserService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 16:19
 */
public interface CreateUserService {

    /**
     * 创建新用户
     *
     * @param phoneNumber
     * @param name
     * @return
     */
    AcUser createUserWithName(String phoneNumber, String name);

    /**
     * 创建新用户
     *
     * @param userId   用户名
     * @param password 密码
     * @return
     */
    AcUser createUser(String userId, String password);

    /**
     * 增加用户
     *
     * @param userId      用户id
     * @param password    密码
     * @param name        用户名
     * @param phoneNumber 手机号
     * @param email       邮箱
     * @param avatar      头像
     * @return
     */
    AcUser createUser(String userId, String password, String name, String phoneNumber, String email, String avatar);

    /**
     * 修改账户状态
     *
     * @param customerNumber 用户编号
     * @param status         状态
     * @return 用户名
     */
    String changeAccountStatus(String customerNumber, Integer status);

    /**
     * 将微信用户合并至已有手机号账户
     *
     * @param unionId
     * @param nickName
     * @param phoneNumber
     */
    void mergeWechatPhoneNumber(String unionId, String nickName, String phoneNumber);

    /**
     * 将白名单用户加入用户表
     *
     * @param account
     * @return 用户
     */
    AcUser createUser(AcAccount account);

    /**
     * 绑定默认角色
     *
     * @param user 用户
     */
    void bindDefaultRole(AcUser user);
}
