package com.bidr.kernel.utils;

import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HttpUtil {

    private final static String COMMA = ",";
    private final static String COOKIE = "Cookie";

    /**
     * Extract request header
     *
     * @param req http request
     * @return
     */
    public static String getHeader(HttpServletRequest req, String name) {
        return req.getHeader(name);
    }

    public static Map<String, String> getHeadersInfoMap(HttpServletRequest req) {
        Map<String, String> map = new HashMap<>(16);
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            // 排除Cookie字段
            if (key.equalsIgnoreCase(COOKIE)) {
                continue;
            }
            String value = req.getHeader(key);
            map.put(key, value);
        }
        return map;
    }

    public static <T> T getParamMap(HttpServletRequest request, Class<T> clazz) {
        Map<String, Object> map = getParamMap(request);
        return JsonUtil.readJson(map, clazz);
    }

    public static Map<String, Object> getParamMap(HttpServletRequest request) {
        Map<String, String[]> requestMap = request.getParameterMap();
        Map<String, Object> map = new HashMap<>(requestMap.size());
        for (Map.Entry<String, String[]> entry : requestMap.entrySet()) {
            String[] valueArr = entry.getValue();
            String value = "";
            if (valueArr != null && valueArr.length > 0 && valueArr[0] != null) {
                value = valueArr[0].trim();
            }
            map.put(entry.getKey(), value);
        }
        return map;
    }

    public static String getRemoteIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (isUnknown(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (isUnknown(ip)) {
            ip = request.getRemoteAddr();
        }

        if (StringUtils.isNotBlank(ip) && ip.contains(",")) {
            String[] list = StringUtils.split(ip, ",");
            if (list != null && list.length > 1) {
                ip = list[0];
            }
        }

        return cutLength(ip);
    }

    private static boolean isUnknown(String ip) {
        return StringUtils.isBlank(ip) || "unknown".equalsIgnoreCase(ip);
    }

    private static String cutLength(String ip) {
        String tempIp = ip;

        final int size = 20;
        if (StringUtils.isNotBlank(ip) && ip.length() > size) {
            tempIp = ip.substring(0, size);
        }

        return tempIp;
    }

    public static String getBodyAsChars(HttpServletRequest request) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            br = request.getReader();
            String str;
            while ((str = br.readLine()) != null) {
                sb.append(str);
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != br) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    public static byte[] getBodyAsBytes(HttpServletRequest request) {

        int len = request.getContentLength();
        byte[] buffer = new byte[len];
        ServletInputStream in = null;

        try {
            in = request.getInputStream();
            in.read(buffer, 0, len);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != in) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return buffer;
    }

    /**
     * @param request
     * @param response
     * @Title: contentDisposition
     * @Description:解决不同浏览器上文件下载的中文名乱码问题
     * @paramfilename导出/下载的文件的文件名
     */

    public static void contentDisposition(String filename, HttpServletRequest request, HttpServletResponse response) {
        try {
            final String userAgent = request.getHeader("USER-AGENT");
            log.info(userAgent);
            if (StringUtils.contains(userAgent, "Trident") || StringUtils.contains(userAgent, "Edge") ||
                    StringUtils.contains(userAgent, "Chrome")) {
                filename = URLEncoder.encode(filename, "UTF-8");
            } else if (StringUtils.contains(userAgent, "Mozilla")) {
                filename = new String(filename.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
            } else {
                filename = URLEncoder.encode(filename, "UTF-8");
            }
            response.setHeader("Access-Control-Expose-Headers", "Content-disposition");
            response.setHeader("Content-disposition", "attachment;filename=\"" + filename + "\"");
        } catch (UnsupportedEncodingException e) {
            log.error("UnsupportedEncodingException.");
        }
    }

    public static void setExcelExportContent(HttpServletRequest request, HttpServletResponse response,
                                             String fileName) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        int suffixIndex = fileName.lastIndexOf(".");
        if (suffixIndex != -1) {
            String suffix = fileName.substring(suffixIndex);
            fileName = fileName.substring(0, suffixIndex) + "-" + DateUtil.formatDate(new Date(), DateUtil.DATE_TIME) +
                    suffix;
        }
        contentDisposition(fileName, request, response);
    }

    public static void export(HttpServletRequest request, HttpServletResponse response, String contentType,
                              String charset, String fileName, byte[] buffers) {
        contentDisposition(fileName, request, response);
        // response.reset();
        response.setContentType(contentType);
        response.setCharacterEncoding(charset);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(buffers);
            outputStream.flush();
        } catch (Exception e) {
            Validator.assertException(e);
        }
    }

    public static boolean systemRequest(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getRequestURI().matches(".*/(actuator|webjars|v3|captcha.*|csrf|swagger.*|error|web/log/fetch).*");
    }

    public static InputStream getStream(String url) throws IOException {
        URLConnection con = new URL(url).openConnection();
        return con.getInputStream();
    }

    public static InputStream getStream(String url, Proxy proxy) throws IOException {
        URLConnection con = new URL(url).openConnection(proxy);
        return con.getInputStream();
    }

    public static File getTempFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        int index = originalFilename.lastIndexOf(".");
        File tempFile;
        if (index != -1) {
            tempFile = File.createTempFile(originalFilename.substring(0, index), originalFilename.substring(index));
        } else {
            tempFile = File.createTempFile(originalFilename + RandomUtil.createRandomLetter(5), ".tmp");
        }
        file.transferTo(tempFile);
        return tempFile;
    }
}

