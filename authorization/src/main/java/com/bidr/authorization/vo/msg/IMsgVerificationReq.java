package com.bidr.authorization.vo.msg;

/**
 * Title: IMsgVerificationReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/10 17:56
 * @description Project Name: Seed
 * @Package: com.srct.service.vo
 */

public interface IMsgVerificationReq {
    /**
     * 获取手机号
     *
     * @return
     */
    String getPhoneNumber();

    /**
     * 获取短信验证码
     *
     * @return
     */
    String getMsgCode();


}
