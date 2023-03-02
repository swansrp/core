/**
 * @Title: RestServiceImpl.java Copyright (c) 2019 Sharp. All rights reserved.
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.service.impl
 * @author sharp
 * @date 2019-01-23 09:38:00
 */
package com.bidr.framework.service.rest;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

/**
 * @author sharp
 */
@Slf4j
@Service
public class RestServiceImpl implements RestService {

    private static final int BUFFER_SIZE = 4096;

    @Autowired
    private RestTemplate restTemplate;
//    @Resource
//    private PushGatewayService pushGatewayService;

    @Override
    public <T> T get(String url, Class<T> clazz) {
        return getSelf().exec(url, HttpMethod.GET, new HttpHeaders(), clazz, null, null);
    }

    private RestService getSelf() {
        return BeanUtil.getBean(this.getClass());
    }

    @Override
    public <T> T get(String url, HttpHeaders header, Class<T> clazz) {
        return getSelf().exec(url, HttpMethod.GET, header, clazz, null, null);
    }

    @Override
    public <T> T get(String url, Object param, Class<T> clazz) {
        return getSelf().exec(url, HttpMethod.GET, new HttpHeaders(), clazz, param, null);
    }

    @Override
    public <T> T get(String url, HttpHeaders header, Object param, Class<T> clazz) {
        return getSelf().exec(url, HttpMethod.GET, header, clazz, param, null);
    }

    @Override
    public <T> T post(String url, Class<T> clazz, Object body) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), clazz, null, body);
    }

    @Override
    public <T> T post(String url, Class<T> clazz, LinkedMultiValueMap body) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), clazz, null, body);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Class<T> clazz, Object body) {
        return getSelf().exec(url, HttpMethod.POST, header, clazz, null, body);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Class<T> clazz, LinkedMultiValueMap body) {
        return getSelf().exec(url, HttpMethod.POST, header, clazz, null, body);
    }

    @Override
    public <T> T post(String url, Class<T> clazz, Object param, Object body) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), clazz, param, body);
    }

    @Override
    public <T> T post(String url, Class<T> clazz, Object param, LinkedMultiValueMap body) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), clazz, param, body);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Class<T> clazz, Object param, Object body) {
        return getSelf().exec(url, HttpMethod.POST, header, clazz, param, body);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Class<T> clazz, Object param, LinkedMultiValueMap body) {
        return getSelf().exec(url, HttpMethod.POST, header, clazz, param, body);
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 4, backoff = @Backoff(delay = 1000L, multiplier = 1))
    public <T> T exec(String url, HttpMethod method, HttpHeaders header, Class<T> clazz, Object param, Object body) {
        ResponseEntity<T> response;
        ParameterizedTypeReference<T> parameter = new ParameterizedTypeReference<T>() {
        };
        if (param != null) {
            url = buildUrl(url, param);
        }
        log.debug("[{}] ==> {}", method, url);
        log.debug("[header] : {}", JsonUtil.toJson(header));
        HttpEntity<Object> entity = new HttpEntity<>(body, header);
        try {
            log.debug("==> {}", JsonUtil.toJson(body));
            long startTime = System.currentTimeMillis();
            response = restTemplate.exchange(url, method, entity, parameter);
            long endTime = System.currentTimeMillis();
            String duration = String.valueOf(endTime - startTime);
//            pushGatewayService.pushGateWay(
//                    new RestTemplatePrometheusBO(url.split("\\?")[0], CommonConst.YES), Double.parseDouble(duration),
//                    true);
            try {
                log.debug("<== {}", JsonUtil.toJson(response.getBody()));
                return JsonUtil.readJson(JsonUtil.toJson(response.getBody()), clazz);
            } catch (Exception e) {
                return response.getBody();
            }
        } catch (Exception e) {
//            pushGatewayService.pushGateWay(new RestTemplatePrometheusBO(url, CommonConst.NO));
            log.error(e.getMessage());
            return null;
        }
    }

    public String buildUrl(String url, Object param) {
        String paramUrl = url.contains("?") ? "&" : "?";
        Map<String, Object> paramMap = ReflectionUtil.getHashMap(param);
        for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
            try {
                paramUrl =
                        paramUrl + entry.getKey() + "=" + URLEncoder.encode(entry.getValue().toString(), "UTF-8") + "&";
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return url + paramUrl.substring(0, paramUrl.lastIndexOf('&'));
    }

    @Override
    public void saveFile(String destUrl, File file) throws IOException {
        FileOutputStream fos = null;
        BufferedInputStream bis = null;
        HttpURLConnection httpconn = null;
        URL url = null;
        byte[] buf = new byte[BUFFER_SIZE];
        int size = 0;

        // 建立链接
        url = new URL(destUrl);
        httpconn = (HttpURLConnection) url.openConnection();
        // 连接指定的资源
        httpconn.connect();
        // 获取网络输入流
        bis = new BufferedInputStream(httpconn.getInputStream());
        // 建立文件
        fos = new FileOutputStream(file);
        log.debug("download[" + destUrl + "]to save the file[" + file.getName()
                + "]");

        // 保存文件
        while ((size = bis.read(buf)) != -1) {
            fos.write(buf, 0, size);
        }
        fos.close();
        bis.close();
        httpconn.disconnect();
    }
}
