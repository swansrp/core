package com.bidr.authorization.service.sms;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.msg.IMsgVerification;
import com.bidr.authorization.vo.msg.MsgVerificationReq;

/**
 * Title: MsgVerificationService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 10:05
 */
public interface MsgVerificationService {
    /**
     * @param msgCodeType
     * @return
     */
    IMsgVerification getMsgCodeType(String msgCodeType);

    /**
     * Generate msg code string.
     *
     * @param phoneNumber phoneNumber
     * @param msgCodeType the type
     * @return the string
     */
    String generateMsgCode(String phoneNumber, IMsgVerification msgCodeType);

    /**
     * Validate msg code.
     *
     * @param token       the token
     * @param phoneNumber phoneNumber
     * @param code        the code
     * @param type        the type
     */
    void validateMsgCode(TokenInfo token, String phoneNumber, String code, String type);

    /**
     * Validate msg code.
     *
     * @param token              the token
     * @param msgVerificationReq 验证码信息
     */
    void validateMsgCode(TokenInfo token, MsgVerificationReq msgVerificationReq, String type);


}
