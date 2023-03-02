/**
 * Title: ConfigFormat.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-7-26 21:55
 * @description Project Name: Grote
 * @Package: com.srct.service.format
 */
package com.bidr.kernel.validate.format;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;

import java.util.Collection;
import java.util.Map;

public class ConfigFormat {

    public static void checkNotNull(Object obj, ErrCode errCode, String message, String key) {
        Validator.assertNotNull(obj, errCode, message, key);
    }

    public static void checkNotNull(Object obj, String message, String key) {
        Validator.assertNotNull(obj, ErrCodeSys.SYS_CONFIG_NOT_EXIST, message, key);
    }

    public static void checkNotEmpty(Map obj, String message, String key) {
        Validator.assertNotEmpty(obj, ErrCodeSys.SYS_CONFIG_NOT_EXIST, message, key);
    }

    public static void checkNotEmpty(Collection obj, String message, String key) {
        Validator.assertNotEmpty(obj, ErrCodeSys.SYS_CONFIG_NOT_EXIST, message, key);
    }
}
