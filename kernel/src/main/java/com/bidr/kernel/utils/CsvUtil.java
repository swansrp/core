package com.bidr.kernel.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Title: CsvUtil
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/25 17:15
 */
public class CsvUtil {

    public static <T> byte[] exportCSV(List<T> dataList, Class<T> clazz) {
        LinkedHashSet<String> headers = ReflectionUtil.getFieldDisplaySet(clazz);
        List<LinkedHashMap<String, String>> exportData = new ArrayList<>();
        if (FuncUtil.isNotEmpty(dataList)) {
            for (T data : dataList) {
                LinkedHashMap<String, String> res = new LinkedHashMap<>();
                for (Field field : ReflectionUtil.getFields(clazz)) {
                    String name = field.getName();
                    String value = StringUtil.parse(ReflectionUtil.getValue(data, field));
                    ApiModelProperty apiModelProperty = field.getAnnotation(ApiModelProperty.class);
                    if (apiModelProperty != null && StringUtils.isNotEmpty((apiModelProperty).value())) {
                        res.put(apiModelProperty.value(), value);
                    } else {
                        res.put(name, value);
                    }
                }
                exportData.add(res);
            }
        }
        return exportCSV(headers, exportData);
    }


    /**
     * 导出csv文件
     *
     * @param headers    内容标题
     *                   注意：headers类型是LinkedHashMap，保证遍历输出顺序和添加顺序一致。
     *                   而HashMap的话不保证添加数据的顺序和遍历出来的数据顺序一致，这样就出现
     *                   数据的标题不搭的情况的
     * @param exportData 要导出的数据集合
     * @return
     */
    public static byte[] exportCSV(LinkedHashSet<String> headers, List<LinkedHashMap<String, String>> exportData) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BufferedWriter buffCvsWriter = null;

        try {
            // 编码gb2312，处理excel打开csv的时候会出现的标题中文乱码
            buffCvsWriter = new BufferedWriter(new OutputStreamWriter(baos, Charset.forName("GBK")));
            // 写入cvs文件的头部

            for (Iterator<String> propertyIterator = headers.iterator(); propertyIterator.hasNext(); ) {
                String title = propertyIterator.next();
                buffCvsWriter.write("\"" + title + "\"");
                if (propertyIterator.hasNext()) {
                    buffCvsWriter.write(",");
                }
            }
            buffCvsWriter.newLine();
            if (FuncUtil.isNotEmpty(exportData)) {
                // 写入文件内容
                LinkedHashMap<String, String> row;
                Map.Entry<String, String> propertyEntry;
                for (Iterator<LinkedHashMap<String, String>> iterator = exportData.iterator(); iterator.hasNext(); ) {
                    row = iterator.next();
                    for (Iterator<String> propertyIterator = headers.iterator(); propertyIterator.hasNext(); ) {
                        String title = propertyIterator.next();
                        String content = StringUtils.isNotBlank(row.get(title)) ? row.get(title) : "";
                        buffCvsWriter.write("\"" + content + "\"");
                        if (propertyIterator.hasNext()) {
                            buffCvsWriter.write(",");
                        }
                    }
                    if (iterator.hasNext()) {
                        buffCvsWriter.newLine();
                    }
                }
            }
            // 记得刷新缓冲区，不然数可能会不全的，当然close的话也会flush的，不加也没问题
            buffCvsWriter.flush();
        } catch (IOException e) {

        } finally {
            // 释放资源
            if (buffCvsWriter != null) {
                try {
                    buffCvsWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return baos.toByteArray();
    }

}
