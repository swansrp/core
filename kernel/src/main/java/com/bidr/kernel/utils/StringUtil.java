/**
 * Copyright ?2018 SRC-TJ Service TG. All rights reserved.
 *
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.utils
 * @author: ruopeng.sha
 * @since: 2018-11-06 14:04
 */
package com.bidr.kernel.utils;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author sharp
 */
public class StringUtil {

    public static final String SPACE = " ";
    public static final String EMPTY = "";
    public static final String SPLITTER = "###";
    public static final String COMMA = ",";
    public static final String DOT = ".";
    public static final String NULL = "null";

    public static final String REGEX_SCRIPT = "<script[^>]*?>[\\s\\S]*?<\\/script>";

    private static final String YES = "1";
    private static final String NO = "0";

    public static String uppserForShort(String str) {
        String res = "";
        if (StringUtils.isNotBlank(str)) {
            String[] ss = str.split("(?<!^)(?=[A-Z])");
            for (String s : ss) {
                res += s;
            }
        }
        if (res.length() < 3) {
            res = str.substring(0, 3).toUpperCase();
        }
        return res;
    }

    public static String cleanScriptFormat(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        Pattern pattern = Pattern.compile(REGEX_SCRIPT, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll("");
    }

    public static String convertSwitch(boolean bool) {
        return bool ? YES : NO;
    }

    public static boolean convertSwitch(String str) {
        return YES.equals(str);
    }

    public static boolean equals(String a, String b) {
        if (a == null || b == null) {
            return false;
        } else {
            return a.equals(b);
        }
    }

    public static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        } else {
            return a.equalsIgnoreCase(b);
        }
    }

    public static String firstLowerCamelCase(String str) {
        if (StringUtils.isNotBlank(str)) {
            String[] strs = str.split("_");
            if (strs.length == 1) {
                return str.substring(0, 1).toLowerCase() + str.substring(1);
            } else {
                String convertedStr = "";
                for (int i = 0; i < strs.length; i++) {
                    convertedStr += firstLetterUpper(strs[i]);
                }
                return convertedStr.substring(0, 1).toLowerCase() + convertedStr.substring(1);
            }
        }
        return str;
    }

    public static String firstLetterUpper(String str) {
        if (StringUtils.isNotBlank(str)) {
            str = str.toLowerCase();
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
        return str;
    }

    public static String firstUpperCamelCase(String str) {
        if (StringUtils.isNotBlank(str)) {
            String[] strs = str.split("_");
            if (strs.length == 1) {
                return str.substring(0, 1).toUpperCase() + str.substring(1);
            } else {
                String convertedStr = "";
                for (int i = 0; i < strs.length; i++) {
                    convertedStr += firstLetterUpper(strs[i]);
                }
                return convertedStr.substring(0, 1).toUpperCase() + convertedStr.substring(1);
            }
        }
        return str;
    }

    public static String join(String... str) {
        return joinWith(SPLITTER, str);
    }

    public static String joinWith(String spliter, String... str) {
        StringBuilder sb = new StringBuilder();
        for (String s : str) {
            sb.append(spliter).append(s);
        }
        return sb.toString().replaceFirst(spliter, "");
    }

    public static String joinWith(String spliter, Collection<String> list) {
        StringBuilder sb = new StringBuilder();
        String[] strArray = new String[list.size()];
        list.toArray(strArray);
        return joinWith(spliter, strArray);
    }

    public static List<String> split(String str, String spliter) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        String[] strArr = str.split(spliter);
        if (strArr.length == 0) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (String s : strArr) {
            if (StringUtils.isNotBlank(s)) {
                list.add(s);
            }
        }
        return list;
    }

    public static String parse(Object obj) {
        return parse(obj, DateUtil.DATE_TIME_NORMAL);
    }

    public static String parse(Object obj, String dateFormat) {
        if (obj == null) {
            return EMPTY;
        }
        if (obj instanceof Date) {
            return DateUtil.formatDate((Date) obj, dateFormat);
        }
        if (obj instanceof Double) {
            return MathUtil.convert((Double) obj).toPlainString();
        }
        if (obj instanceof BigDecimal) {
            return ((BigDecimal) obj).toPlainString();
        }
        return obj.toString();
    }

    /***
     * 将驼峰转为下划线
     **/
    public static String camelToUnderline(String str) {
        Pattern compile = Pattern.compile("[A-Z]");
        Matcher matcher = compile.matcher(str);
        StringBuffer sb = new StringBuffer();
        while(matcher.find()) {
            matcher.appendReplacement(sb,  "_" + matcher.group(0).toLowerCase());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /***
     * 将下划线转为驼峰
     **/
    public static String underlineToCamel(String str) {
        str = str.toLowerCase();
        Pattern compile = Pattern.compile("_[a-z]");
        Matcher matcher = compile.matcher(str);
        StringBuffer sb = new StringBuffer();
        while(matcher.find()) {
            matcher.appendReplacement(sb,  matcher.group(0).toUpperCase().replace("_",""));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
