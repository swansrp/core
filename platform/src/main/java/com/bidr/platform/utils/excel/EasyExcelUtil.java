package com.bidr.platform.utils.excel;

import com.alibaba.excel.EasyExcel;
import com.bidr.platform.service.excel.ModelDataListener;
import com.bidr.platform.service.excel.NoModelDataListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;

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

    public static void read(InputStream is, NoModelDataListener<?> handler) {
        EasyExcel.read(is, handler).sheet().doRead();
    }

    public static void read(File file, NoModelDataListener<?> handler) {
        EasyExcel.read(file, handler).sheet().doRead();
    }

    public static void readAll(InputStream is, NoModelDataListener<?> handler) {
        EasyExcel.read(is, handler).doReadAll();
    }

    public static void readAll(File file, NoModelDataListener<?> handler) {
        EasyExcel.read(file, handler).doReadAll();
    }

    public static <T> void read(InputStream is, ModelDataListener<?, T> handler, Class<T> clazz) {
        EasyExcel.read(is, clazz, handler).sheet().doRead();
    }

    public static <T> void read(File file, ModelDataListener<?, T> handler, Class<T> clazz) {
        EasyExcel.read(file, clazz, handler).sheet().doRead();
    }

    public static <T> void readAll(InputStream is, ModelDataListener<?, T> handler, Class<T> clazz) {
        EasyExcel.read(is, clazz, handler).doReadAll();
    }

    public static <T> void readAll(File file, ModelDataListener<?, T> handler, Class<T> clazz) {
        EasyExcel.read(file, clazz, handler).doReadAll();
    }
}
