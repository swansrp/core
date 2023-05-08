package com.bidr.authorization.vo.login.pwd;

import com.bidr.authorization.vo.msg.IMsgVerificationReq;
import lombok.Data;

/**
 * Title: MsgChangePasswordReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/4/18 11:56
 * @description Project Name: Mall
 * @Package: com.srct.service.account.vo.login
 */
@Data
public class MsgChangePasswordReq implements IMsgVerificationReq {
    private String phoneNumber;
    private String msgCode;
    private String password;
    private String passwordConfirm;
}
