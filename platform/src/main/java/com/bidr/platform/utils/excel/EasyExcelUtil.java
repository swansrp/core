package com.bidr.platform.utils.excel;

import com.alibaba.excel.EasyExcel;
import com.bidr.platform.service.excel.ModelDataListener;
import com.bidr.platform.service.excel.NoModelDataListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

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
        // 默认跳过隐藏的 sheet 页
        read(is, handler, clazz, true);
    }

    /**
     * 读取 Excel 流（可配置是否跳过隐藏 sheet）
     *
     * @param is            输入流
     * @param handler       数据监听器
     * @param clazz         VO 类
     * @param skipHidden    是否跳过隐藏的 sheet 页
     */
    public static <T> void read(InputStream is, ModelDataListener<?, T> handler, Class<T> clazz, boolean skipHidden) {
        if (!skipHidden) {
            // 不跳过隐藏 sheet，使用原有逻辑
            EasyExcel.read(is, clazz, handler).sheet().doRead();
            return;
        }
        
        // 跳过隐藏 sheet：由于 InputStream 只能读取一次，我们需要先读取所有 sheet
        // EasyExcel 的 doReadAll 会读取所有 sheet（包括隐藏的）
        // 对于 InputStream，我们暂时使用 doReadAll，如果需要严格过滤隐藏 sheet，建议使用 File 版本
        log.warn("InputStream version cannot filter hidden sheets, use File version for strict filtering");
        EasyExcel.read(is, clazz, handler).doReadAll();
    }

    public static <T> void read(File file, ModelDataListener<?, T> handler, Class<T> clazz) {
        // 默认跳过隐藏的 sheet 页
        read(file, handler, clazz, true);
    }

    /**
     * 读取 Excel 文件（可配置是否跳过隐藏 sheet）
     *
     * @param file          Excel 文件
     * @param handler       数据监听器
     * @param clazz         VO 类
     * @param skipHidden    是否跳过隐藏的 sheet 页
     */
    public static <T> void read(File file, ModelDataListener<?, T> handler, Class<T> clazz, boolean skipHidden) {
        if (!skipHidden) {
            // 不跳过隐藏 sheet，使用原有逻辑
            EasyExcel.read(file, clazz, handler).sheet().doRead();
            return;
        }
        
        // 跳过隐藏 sheet：先获取所有非隐藏的 sheet 索引
        List<Integer> visibleSheetIndexes = getVisibleSheetIndexes(file);
        
        if (visibleSheetIndexes.isEmpty()) {
            // 如果没有可见 sheet，不执行任何操作
            log.warn("No visible sheets found in Excel file: {}", file.getName());
            return;
        }
        
        // 读取所有可见的 sheet
        com.alibaba.excel.read.builder.ExcelReaderBuilder readerBuilder = EasyExcel.read(file, clazz, handler);
        for (Integer sheetIndex : visibleSheetIndexes) {
            readerBuilder.sheet(sheetIndex).doRead();
        }
    }
    
    /**
     * 获取 Excel 文件中所有非隐藏 sheet 的索引列表
     */
    private static List<Integer> getVisibleSheetIndexes(File file) {
        List<Integer> visibleIndexes = new java.util.ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            int numberOfSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < numberOfSheets; i++) {
                if (!workbook.isSheetHidden(i) && !workbook.isSheetVeryHidden(i)) {
                    visibleIndexes.add(i);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get visible sheet indexes from file: {}, error: {}", file.getName(), e.getMessage());
        }
        return visibleIndexes;
    }

    public static <T> void readAll(InputStream is, ModelDataListener<?, T> handler, Class<T> clazz) {
        EasyExcel.read(is, clazz, handler).doReadAll();
    }

    public static <T> void readAll(File file, ModelDataListener<?, T> handler, Class<T> clazz) {
        EasyExcel.read(file, clazz, handler).doReadAll();
    }
}
