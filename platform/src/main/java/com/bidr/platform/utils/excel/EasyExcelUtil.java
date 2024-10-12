package com.bidr.platform.utils.excel;

import com.alibaba.excel.EasyExcel;
import com.bidr.platform.service.excel.EasyExcelHandler;
import com.bidr.platform.service.excel.NoModelDataListener;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;

/**
 * Title: EasyExcelUtil
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/08 16:48
 */
@Slf4j
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

    public static void read(InputStream is, EasyExcelHandler<?> handler) {
        EasyExcel.read(is, new NoModelDataListener<>(handler)).sheet().doRead();
    }

    public static void read(File file, EasyExcelHandler<?> handler) {
        EasyExcel.read(file, new NoModelDataListener<>(handler)).sheet().doRead();
    }
}
