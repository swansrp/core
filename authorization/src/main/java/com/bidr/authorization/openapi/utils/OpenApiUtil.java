package com.bidr.authorization.openapi.utils;

import com.bidr.authorization.dto.SignDTO;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.DesUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.kernel.validate.Validator;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import java.util.UUID;

/**
 * Title: OpenApiUtil
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 12:44
 */
public class OpenApiUtil {
    public static void validateSign(long timeStamp, String nonce, String signature, String appSecret) {
        Date now = new Date();
        Date reqDate = new Date(timeStamp);
        long secondDiff = DateUtil.secondDiff(reqDate, now);
        Validator.assertFalse(Math.abs(secondDiff) > 600, ErrCodeSys.SYS_VALIDATE_NOT_PASS, "时间戳");
        String text = Md5Util.MD5(timeStamp + nonce).toUpperCase();
        if (StringUtils.isNotBlank(appSecret)) {
            try {
                String deCrypt = DesUtil.decrypt(signature, appSecret);
                Validator.assertTrue(StringUtils.equals(deCrypt, text), ErrCodeSys.SYS_VALIDATE_NOT_PASS, "签名");
            } catch (Exception e) {
                throw new ServiceException("鉴权失败");
            }
        } else {
            throw new ServiceException("秘钥信息不存在");
        }
    }

    public static SignDTO sign(String appSecret) {
        return sign(System.currentTimeMillis(), UUID.randomUUID().toString(), appSecret);
    }

    public static SignDTO sign(long timeStamp, String nonce, String secret) {
        SignDTO sign = new SignDTO();
        sign.setNonce(nonce);
        sign.setTimeStamp(timeStamp);
        String text = Md5Util.MD5(timeStamp + nonce).toUpperCase();
        if (StringUtils.isNotBlank(secret)) {
            try {
                sign.setSignature(DesUtil.encrypt(text, secret));
                return sign;
            } catch (Exception e) {
                throw new ServiceException("加密失败", e);
            }
        } else {
            throw new ServiceException("秘钥信息不存在");
        }
    }
}
