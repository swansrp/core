package com.bidr.platform.utils.file;

import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.lowagie.text.DocumentException;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;

/**
 * Title: PDFUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/1/7 19:06
 */
public class PdfUtil {

    private static final String FONT = "/SourceHanSansCN-Regular.ttf";

    private PdfUtil() {
    }

    /**
     * 使用 iText 生成 PDF 文档
     *
     * @param htmlTmpStr html 模板文件字符串
     * @param fontFile   所需字体文件(相对路径+文件名)
     */
    public static void createPdfByHtmlTemplate(File pdfFile, String htmlTmpStr, String fontFile, String baseUrl) {
        htmlTmpStr = htmlTmpStr.replaceAll("&([^;]+(?!(?:\\w|;)))", "&amp;$1");
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(pdfFile);
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlTmpStr, "classpath:/" + baseUrl);
            ITextFontResolver fontResolver = renderer.getFontResolver();
            if (StringUtils.isEmpty(fontFile)) {
                fontFile = FONT;
            }
            fontResolver.addFont(fontFile, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            renderer.layout();
            renderer.createPDF(outputStream);
            renderer.finishPDF();
        } catch (IOException | DocumentException e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "创建PDF失败");
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }


    }

    /**
     * 使用 iText 生成 PDF 文档
     *
     * @param infoMap                 pdf模板对应内容
     * @param fileTemplateInputStream pdf模板
     * @param baseFont                所需字体文件
     */
    public static void createPdfByPdfTemplate(File pdfFile, HashMap<String, String> infoMap, InputStream fileTemplateInputStream, BaseFont baseFont) {
        PdfReader reader;
        PdfStamper stamper = null;


        try {
            if (baseFont == null) {
                baseFont = BaseFont.createFont(FONT, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            }
            reader = new PdfReader(fileTemplateInputStream);
            stamper = new PdfStamper(reader, Files.newOutputStream(pdfFile.toPath()));
            AcroFields form = stamper.getAcroFields();
            if (CollectionUtils.isNotEmpty(form.getFields().keySet())) {
                for (String key : form.getFields().keySet()) {
                    form.setFieldProperty(key, "textfont", baseFont, null);
                    form.setField(key, infoMap.get(key));
                }
            }
            stamper.setFormFlattening(true);
        } catch (com.itextpdf.text.DocumentException | IOException e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "创建PDF失败");
        } finally {
            if (stamper != null) {
                try {
                    stamper.close();
                } catch (com.itextpdf.text.DocumentException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
