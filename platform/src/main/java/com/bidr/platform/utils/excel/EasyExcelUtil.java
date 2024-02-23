package com.bidr.platform.utils.excel;

/**
 * Title: EasyExcelUtil
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/08 16:48
 */
public class EasyExcelUtil {
    public static String getExcelLine(int num) {
        String line = "";
        int first = num / 26;
        int second = num % 26;
        if (first > 0) {
            line = (char) ('A' + first - 1) + "";
        }
        line += (char) ('A' + second) + "";
        return line;
    }
}
