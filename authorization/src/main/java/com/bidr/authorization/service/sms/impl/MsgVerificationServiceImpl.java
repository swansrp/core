package com.bidr.authorization.service.sms.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.msg.IMsgVerification;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.service.sms.MsgVerificationService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.msg.MsgVerificationReq;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.RandomUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: MsgVerificationServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/27 10:05
 */
@Service
@RequiredArgsConstructor
public class MsgVerificationServiceImpl implements MsgVerificationService {

    private static final Map<String, MsgCodeType> MSG_CODE_TYPE_MAP = new ConcurrentHashMap<>(16);

    private static final String INTERNAL_TIMESTAMP = "_INTERNAL_TIMESTAMP";
    private static final String EXPIRED_TIMESTAMP = "_EXPIRED_TIMESTAMP";
    private static final String PHONE_NUMBER = "_PHONE_NUMBER";

    private final TokenService tokenService;
    private final SysConfigCacheService frameCacheService;

    @Override
    public IMsgVerification getMsgCodeType(String msgCodeType) {
        return MSG_CODE_TYPE_MAP.getOrDefault(msgCodeType, null);
    }

    @Override
    public String generateMsgCode(String phoneNumber, IMsgVerification msgCodeType) {
        String code = RandomUtil.createRandomNum(msgCodeType.getLength());
        TokenInfo token = tokenService.getToken();

        String internalTimeStamp = tokenService.getItem(token, getInternalTimestampTokenItem(msgCodeType.name()),
                String.class);
        if (StringUtils.isNotBlank(internalTimeStamp)) {
            Validator.assertTrue(Long.parseLong(internalTimeStamp) <= System.currentTimeMillis(),
                    AccountErrCode.AC_MSG_CODE_INTERNAL);
        }
        fillMsgCodeToken(token, msgCodeType.name(), phoneNumber, code, msgCodeType.getInternal(),
                msgCodeType.getTimeout());
        return code;
    }

    private String getInternalTimestampTokenItem(String type) {
        return type + INTERNAL_TIMESTAMP;
    }

    private void fillMsgCodeToken(TokenInfo token, String type, String phoneNumber, String code, long internalTimestamp,
                                  long expiredTimestamp) {
        Map<String, Object> map = tokenService.getTokenValue();
        map.put(type, code);
        map.put(getPhoneNumberTokenItem(type), phoneNumber);
        map.put(getInternalTimestampTokenItem(type),
                String.valueOf(System.currentTimeMillis() + internalTimestamp * 1000));
        map.put(getExpiredTimestampTokenItem(type),
                String.valueOf(System.currentTimeMillis() + expiredTimestamp * 1000));
        tokenService.setTokenValue(token, map);
    }

    private String getPhoneNumberTokenItem(String type) {
        return type + PHONE_NUMBER;
    }

    private String getExpiredTimestampTokenItem(String type) {
        return type + EXPIRED_TIMESTAMP;
    }

    @Override
    public void validateMsgCode(TokenInfo token, String phoneNumber, String code, String msgCodeType) {
        Validator.assertNotNull(token, ErrCodeSys.PA_DATA_NOT_EXIST, "token");
        Validator.assertNotBlank(code, ErrCodeSys.PA_DATA_NOT_EXIST, "短信验证码");
        String msgPhoneNumber = tokenService.getItem(token, getPhoneNumberTokenItem(msgCodeType), String.class);
        Validator.assertNotNull(msgPhoneNumber, AccountErrCode.AC_NO_GET_MSG_CODE);
        Validator.assertTrue(StringUtils.equals(phoneNumber, msgPhoneNumber), ErrCodeSys.PA_DATA_DIFF, "手机号码");
        String msgCode = tokenService.getItem(token, msgCodeType, String.class);
        Long timeStamp = tokenService.getItem(token, getExpiredTimestampTokenItem(msgCodeType), Long.class);
        Validator.assertFalse(timeStamp <= System.currentTimeMillis(), AccountErrCode.AC_MSG_CODE_EXPIRED);
        if (!frameCacheService.getParamSwitch(AccountParam.TEST_MODE_SMS_SEND_SWITCH)) {
            Validator.assertMatch(msgCode, code.toLowerCase(), AccountErrCode.AC_MSG_CODE_ERROR);
        }
        cleanMsgCodeToken(token, msgCodeType);
    }

    @Override
    public void validateMsgCode(TokenInfo token, MsgVerificationReq req, String msgCodeType) {
        validateMsgCode(token, req.getPhoneNumber(), req.getMsgCode(), msgCodeType);
    }

    private void cleanMsgCodeToken(TokenInfo token, String type) {
        tokenService.removeItemByToken(token, type, getPhoneNumberTokenItem(type), getInternalTimestampTokenItem(type),
                getExpiredTimestampTokenItem(type));
    }

    @PostConstruct
    public void init() {
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<? extends IMsgVerification>> msgVerificationClass = reflections.getSubTypesOf(IMsgVerification.class);
        for (Class<? extends IMsgVerification> clazz : msgVerificationClass) {
            if (Enum.class.isAssignableFrom(clazz)) {
                for (IMsgVerification enumItem : clazz.getEnumConstants()) {
                    if (StringUtils.isNotBlank(enumItem.name())) {
                        MSG_CODE_TYPE_MAP.put(enumItem.name(),
                                new MsgCodeType(enumItem.name(), enumItem.getInternal(), enumItem.getTimeout(),
                                        enumItem.getLength()));
                    }
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class MsgCodeType implements IMsgVerification {
        private String name;
        private int internal;
        private int timeout;
        private int length;

        @Override
        public String name() {
            return this.name;
        }
    }
}
