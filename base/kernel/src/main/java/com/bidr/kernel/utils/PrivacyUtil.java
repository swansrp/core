package com.bidr.kernel.utils;

/**
 * Title: PrivacyUtil
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/12/16 22:49
 */

public class PrivacyUtil {

    public static String idCardPrivacy(String idCard) {
        if (FuncUtil.isEmpty(idCard)) {
            if (idCard.length() > 6) {
                return idCard.substring(0, 6) + ("********") + idCard.substring(idCard.length() - 4);
            }
        }
        return idCard;
    }

    public static String phoneNumber(String phoneNumber) {
        if (FuncUtil.isEmpty(phoneNumber)) {
            if (phoneNumber.length() > 4) {
                return phoneNumber.substring(0, 3) + ("****") + phoneNumber.substring(phoneNumber.length() - 4);
            }
        }
        return phoneNumber;
    }
}
