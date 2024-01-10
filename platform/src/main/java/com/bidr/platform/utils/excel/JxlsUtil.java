package com.bidr.platform.utils.excel;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.jxls.common.Context;
import org.jxls.expression.JexlExpressionEvaluator;
import org.jxls.transform.Transformer;
import org.jxls.util.JxlsHelper;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: JxlsUtil
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/08 22:47
 */

public class JxlsUtil {
    /**
     * 本地的excel导出
     *
     * @param xls
     * @param out
     * @param model
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void exportExcel(File xls, File out, Map<String, Object> model)
            throws FileNotFoundException, IOException {
        exportExcel(new FileInputStream(xls), new FileOutputStream(out), model);
    }

    /**
     * 导出excel函数
     *
     * @param is
     * @param os
     * @param model
     * @throws IOException
     */
    public static void exportExcel(InputStream is, OutputStream os, Map<String, Object> model) throws IOException {
        Context context = new Context();
        if (model != null) {
            for (String key : model.keySet()) {
                context.putVar(key, model.get(key));
            }
        }
        JxlsHelper jxlsHelper = JxlsHelper.getInstance();
        Transformer transformer = jxlsHelper.createTransformer(is, os);
        JexlExpressionEvaluator evaluator = (JexlExpressionEvaluator) transformer.getTransformationConfig()
                .getExpressionEvaluator();
        Map<String, Object> func = new HashMap<>(0);
        // 添加自定义功能，对应着excel模板中的utils
        func.put("utils", new JxlsUtil());

        /**
         * 这里需要注意，只加在map里面是不生效的，一定要跟后面的代码，才正确
         */
        JexlBuilder jb = new JexlBuilder();
        jb.namespaces(func);
        JexlEngine je = jb.create();
        evaluator.setJexlEngine(je);

        jxlsHelper.processTemplate(context, transformer);
    }

    /**
     * 日期格式化函数，对应着excel模板中的dateFmt
     * （自定义功能）
     *
     * @param date
     * @param fmt
     * @return
     */
    public String dateFormat(Date date, String fmt) {
        if (date == null) {
            return "";
        }
        try {
            SimpleDateFormat dateFmt = new SimpleDateFormat(fmt);
            return dateFmt.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public Object ifElse(boolean b, Object o1, Object o2) {
        return b ? o1 : o2;
    }
}
