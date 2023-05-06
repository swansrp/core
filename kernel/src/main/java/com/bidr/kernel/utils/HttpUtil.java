package com.bidr.kernel.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    public static void mergeStream(File file, HttpServletResponse response) {
        byte[] buffer = new byte[1024];
        response.reset();
        response.setContentType("application/pdf");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-disposition", "attachment;filename=" + file.getName() + ".pdf");
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        try {
            fis = new FileInputStream(file);
            bis = new BufferedInputStream(fis);
            OutputStream os = response.getOutputStream();
            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                i = bis.read(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean systemRequest(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getRequestURI().matches(".*/(actuator|export|webjars|v3|captcha.*|csrf|swagger.*).*");
    }
}

