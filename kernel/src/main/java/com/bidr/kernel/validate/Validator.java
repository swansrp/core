/**
 * Title: Validator.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-7-26 21:26
 * @description Project Name: Grote
 * @Package: com.srct.service.validate
 */
package com.bidr.kernel.validate;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;

import java.util.Collection;
import java.util.Map;

public class Validator {

    private static final String YES = "1";

    public static void assertException(ErrCode code, String... parameters) {
        throw new ServiceException(code, parameters);
    }

    public static void assertException(Exception e) {
        throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, e.getMessage());
    }

    public static void assertNotBlank(String param, ErrCode code, String... parameters) {
        if (param == null || param.trim().length() == 0) {
            throw new ServiceException(code, parameters);
        }
    }

    public static void assertBlank(String param, ErrCode code, String... parameters) {
        if (param != null && param.trim().length() > 0) {
            throw new ServiceException(code, parameters);
        }
    }

    @SuppressWarnings("rawtypes")
    public static void assertNotEmpty(Map map, ErrCode code, String... parameters) {
        if (map == null || map.size() == 0) {
            throw new ServiceException(code, parameters);
        }
    }

    @SuppressWarnings("rawtypes")
    public static void assertNotEmpty(Collection list, ErrCode code, String... parameters) {
        if (list == null || list.size() == 0) {
            throw new ServiceException(code, parameters);
        }
    }

    @SuppressWarnings("rawtypes")
    public static void assertEmpty(Map map, ErrCode code, String... parameters) {
        if (map != null && map.size() > 0) {
            throw new ServiceException(code, parameters);
        }
    }

    @SuppressWarnings("rawtypes")
    public static void assertEmpty(Collection list, ErrCode code, String... parameters) {
        if (list != null && list.size() > 0) {
            throw new ServiceException(code, parameters);
        }
    }

    public static void assertNotNull(Object param, ErrCode code, String... parameters) {
        if (param == null) {
            throw new ServiceException(code, parameters);
        }
    }

    public static void assertNull(Object param, ErrCode code, String... parameters) {
        if (param != null) {
            throw new ServiceException(code, parameters);
        }
    }

    public static void assertYes(String str, ErrCode code, String... parameters) {
        assertTrue(YES.equals(str), code, parameters);
    }

    public static void assertTrue(Boolean param, ErrCode code, String... parameters) {
        if (!param) {
            throw new ServiceException(code, parameters);
        }
    }

    public static void assertNo(String str, ErrCode code, String... parameters) {
        assertFalse(YES.equals(str), code, parameters);
    }

    public static void assertFalse(Boolean param, ErrCode code, String... parameters) {
        if (true == param) {
            throw new ServiceException(code, parameters);
        }
    }

    public static void assertMatch(String param, String pattern, ErrCode code, String... parameters) {
        assertTrue(param.matches(pattern), code, parameters);
    }

}
