/**
 * Title: CommonFormat.java
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
import com.bidr.kernel.utils.MathUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class CommonFormat {

    // --------------校验参数------------

    public static void checkNotBlank(String param, String message) {
        Validator.assertNotBlank(param, ErrCodeSys.PA_PARAM_NULL, message);
    }

    public static void checkNotNull(Object param, String message) {
        Validator.assertNotNull(param, ErrCodeSys.PA_PARAM_NULL, message);
    }

    // --------------校验正则------------

    public static void checkMatch(String param, String pattern, String message) {
        Validator.assertMatch(param, pattern, ErrCodeSys.PA_PARAM_FORMAT, message);
    }

    public static void checkMatch(String param, String pattern, ErrCode errCode, String message) {
        Validator.assertMatch(param, pattern, errCode, message);
    }

    // --------------校验列表------------

    @SuppressWarnings("rawtypes")
    public static void checkNotEmpty(List list, String message) {
        Validator.assertNotEmpty(list, ErrCodeSys.PA_PARAM_NULL, message);
    }

    // --------------校验长度------------

    public static void checkLengthMin(String param, int min, ErrCode errCode, String message) {
        Validator.assertTrue(param.length() >= min, errCode, message);
    }

    public static void checkLengthMax(String param, int max, ErrCode errCode, String message) {
        Validator.assertTrue(param.length() <= max, errCode, message);
    }

    // --------------校验数据库数据------------

    public static void checkDataNull(Object param, String message) {
        Validator.assertNull(param, ErrCodeSys.PA_DATA_HAS_EXIST, message);
    }

    public static void checkDataExist(Object param, String message) {
        Validator.assertNotNull(param, ErrCodeSys.PA_DATA_NOT_EXIST, message);
    }

    public static void checkDataNull(Object param, ErrCode errCode, String message) {
        Validator.assertNull(param, errCode, message);
    }

    public static void checkDataExist(Object param, ErrCode errCode, String message) {
        Validator.assertNotNull(param, errCode, message);
    }

    // --------------校验数据相同------------

    public static void checkEqual(String a, String b, String message) {
        checkEqual(a, b, ErrCodeSys.PA_DATA_DIFF, message);
    }

    public static void checkEqual(String a, String b, ErrCode errCode, String message) {
        Validator.assertTrue(StringUtils.equals(a, b), errCode, message);
    }

    public static void checkEqualIgnoreCase(String a, String b, String message) {
        checkEqualIgnoreCase(a, b, ErrCodeSys.PA_DATA_DIFF, message);
    }

    public static void checkEqualIgnoreCase(String a, String b, ErrCode errCode, String message) {
        Validator.assertTrue(StringUtils.equalsIgnoreCase(a, b), errCode, message);
    }

    public static void checkEqual(long a, long b, String message) {
        checkEqual(a, b, ErrCodeSys.PA_DATA_DIFF, message);
    }

    public static void checkEqual(long a, long b, ErrCode errCode, String message) {
        Validator.assertTrue(a == b, errCode, message);
    }

    public static void checkEqual(int a, int b, String message) {
        checkEqual(a, b, ErrCodeSys.PA_DATA_DIFF, message);
    }

    public static void checkEqual(int a, int b, ErrCode errCode, String message) {
        Validator.assertTrue(a == b, errCode, message);
    }

    public static void checkEqual(double a, double b, String message) {
        checkEqual(a, b, ErrCodeSys.PA_DATA_DIFF, message);
    }

    public static void checkEqual(double a, double b, ErrCode errCode, String message) {
        Validator.assertTrue(MathUtil.isEqual(a, b), errCode, message);
    }

    public static void checkGreater(double a, double b, ErrCode errCode, String message) {
        Validator.assertTrue(MathUtil.isGreater(a, b), errCode, message);
    }

    public static void checkLess(double a, double b, ErrCode errCode, String message) {
        Validator.assertTrue(MathUtil.isLess(a, b), errCode, message);
    }

    public static void checkGreaterOrEqual(double a, double b, ErrCode errCode, String message) {
        Validator.assertTrue(MathUtil.isGreaterOrEqual(a, b), errCode, message);
    }

    public static void checkLessOrEqual(double a, double b, ErrCode errCode, String message) {
        Validator.assertTrue(MathUtil.isLessOrEqual(a, b), errCode, message);
    }
}
