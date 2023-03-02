package com.sharp.authorization.config.msg;

/**
 * Title: IMsgVerification
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/24 10:02
 */
public interface IMsgVerification {
    /**
     * 获取短信验证码类型名称
     *
     * @return
     */
    String name();

    /**
     * 获取短信验证码获取间隔时间
     *
     * @return
     */
    int getInternal();

    /**
     * 获取短信验证码过期时间
     *
     * @return
     */
    int getTimeout();

    /**
     * 短信验证码长度
     *
     * @return
     */
    int getLength();
}
