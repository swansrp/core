package com.bidr.platform.utils.file;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

/**
 * Title: FreeMarkerUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/8 14:46
 * @description Project Name: customer
 * @Package: com.bhfae.common.utils
 */

@Slf4j
public class FreeMarkerUtil {

    private volatile static Configuration configuration;

    static {
        if (configuration == null) {
            synchronized (FreeMarkerUtil.class) {
                if (configuration == null) {
                    configuration = new Configuration(Configuration.VERSION_2_3_28);
                }
            }
        }
    }

    private FreeMarkerUtil() {
    }

    /**
     * freemarker 引擎渲染 html
     *
     * @param dataMap     传入 html 模板的 Map 数据
     * @param ftlFilePath html 模板文件相对路径(相对于 resources路径,路径 + 文件名)
     *                    eg: "templates/pdf_export_demo.ftl"
     * @return
     */
    public static String freemarkerRender(Map<String, Object> dataMap, String ftlFilePath) {
        Writer out = new StringWriter();
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        try {
            configuration.setClassForTemplateLoading(FreeMarkerUtil.class, "/");
            configuration.setLogTemplateExceptions(false);
            configuration.setWrapUncheckedExceptions(true);
            Template template = configuration.getTemplate(ftlFilePath);
            template.process(dataMap, out);
            out.flush();
            return out.toString();
        } catch (IOException | TemplateException e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static String freemarkerRender(Map<String, String> dataMap, File file) {
        Writer out = new StringWriter();
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        try {
            configuration.setDirectoryForTemplateLoading(file.getParentFile());
            configuration.setLogTemplateExceptions(false);
            configuration.setWrapUncheckedExceptions(true);
            Template template = configuration.getTemplate(file.getName());
            template.process(dataMap, out);
            out.flush();
            return out.toString();
        } catch (IOException e) {
            log.error("", e);
        } catch (TemplateException e) {
            log.error("", e);
        } finally {
            try {
                out.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
        return null;
    }
}
