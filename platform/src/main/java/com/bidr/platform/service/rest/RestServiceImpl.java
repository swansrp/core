/**
 * @Title: RestServiceImpl.java Copyright (c) 2019 Sharp. All rights reserved.
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.service.impl
 * @author sharp
 * @since 2019-01-23 09:38:00
 */
package com.bidr.platform.service.rest;

import cn.hutool.core.util.RandomUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.platform.exception.RestTemplateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
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

    @Lazy
    @Resource
    private RestServiceImpl restServiceImpl;

    @Override
    public <T> T get(String url, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.GET, new HttpHeaders(), null, null, collectionClass, elementClasses);
    }

    private RestService getSelf() {
        return restServiceImpl;
    }

    @Override
    public <T> T get(String url, HttpHeaders header, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.GET, header, null, null, collectionClass, elementClasses);
    }

    @Override
    public <T> T get(String url, Object param, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.GET, new HttpHeaders(), param, null, collectionClass, elementClasses);
    }

    @Override
    public <T> T get(String url, HttpHeaders header, Object param, Class<?> collectionClass,
                     Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.GET, header, param, null, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, Object body, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), null, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, LinkedMultiValueMap body, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), null, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Object body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, header, null, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, LinkedMultiValueMap body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, header, null, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, Object param, Object body, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), param, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, Object param, LinkedMultiValueMap body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, new HttpHeaders(), param, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Object param, Object body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, header, param, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(String url, HttpHeaders header, Object param, LinkedMultiValueMap body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(url, HttpMethod.POST, header, param, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T exec(String url, HttpMethod method, HttpHeaders header, Object param, Object body,
                      Class<?> collectionClass, Class<?>... elementClasses) {
        return exec(restTemplate, url, method, header, param, body, collectionClass, elementClasses);
    }

    @Override
    @Retryable(value = {Exception.class}, maxAttempts = 4, backoff = @Backoff(delay = 1000L, multiplier = 2))
    public <T> T exec(RestTemplate template, String url, HttpMethod method, HttpHeaders header, Object param,
                      Object body, Class<?> collectionClass, Class<?>... elementClasses) {
        ResponseEntity<T> response;
        RestTemplate restTemp = template == null ? restTemplate : template;

        ParameterizedTypeReference<T> parameter = new ParameterizedTypeReference<T>() {
        };
        if (param != null) {
            url = buildUrl(url, param);
        }
        String transactionId = RandomUtil.randomString(7);
        log.debug("[{}][{}] ==> {}", transactionId, method, url);
        log.debug("[{}][header] : {}", transactionId, JsonUtil.toJson(header));
        HttpEntity<Object> entity = new HttpEntity<>(body, header);
        try {
            log.debug("[{}]==> {}", transactionId, JsonUtil.toJson(body));
            long startTime = System.currentTimeMillis();
            response = restTemp.exchange(url, method, entity, parameter);
            long endTime = System.currentTimeMillis();
            String duration = String.valueOf(endTime - startTime);
            try {
                log.debug("[{}]({})<== {}", transactionId, duration, JsonUtil.toJson(response.getBody()));
                return JsonUtil.readJson(response.getBody(), collectionClass, elementClasses);
            } catch (Exception e) {
                return response.getBody();
            }
        } catch (RestTemplateException e) {
            log.error("[{}]通信异常: {}", transactionId, e.getMsg());
            throw e;
        }
    }

    @Override
    public RestTemplate getNoProxyRestTemplate() {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        RestTemplate restTemp = new RestTemplate(httpRequestFactory);
        restTemp.setRequestFactory(httpRequestFactory);
        restTemp.setErrorHandler(restTemplate.getErrorHandler());
        restTemp.setMessageConverters(restTemplate.getMessageConverters());
        restTemp.setInterceptors(restTemplate.getInterceptors());
        return restTemp;
    }

    public String buildUrl(String url, Object param) {
        String paramUrl = url.contains("?") ? "&" : "?";
        Map<String, Object> paramMap = JsonUtil.readJson(param, Map.class, String.class, Object.class);
        for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
            try {
                if (entry.getValue() != null) {
                    if (entry.getValue() instanceof Collection) {
                        for (Object o : ((Collection<?>) entry.getValue())) {
                            paramUrl = paramUrl + entry.getKey() + "=" + URLEncoder.encode(o.toString(), "UTF-8") + "&";
                        }
                    } else {
                        paramUrl = paramUrl + entry.getKey() + "=" +
                                URLEncoder.encode(entry.getValue().toString(), "UTF-8") + "&";
                    }
                }
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
        log.debug("download[" + destUrl + "]to save the file[" + file.getName() + "]");

        // 保存文件
        while ((size = bis.read(buf)) != -1) {
            fos.write(buf, 0, size);
        }
        fos.close();
        bis.close();
        httpconn.disconnect();
    }

    @Override
    public <T> T get(RestTemplate template, String url, Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.GET, new HttpHeaders(), null, null, collectionClass,
                elementClasses);
    }

    @Override
    public <T> T get(RestTemplate template, String url, HttpHeaders header, Class<?> collectionClass,
                     Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.GET, header, null, null, collectionClass, elementClasses);
    }

    @Override
    public <T> T get(RestTemplate template, String url, Object param, Class<?> collectionClass,
                     Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.GET, new HttpHeaders(), param, null, collectionClass,
                elementClasses);
    }

    @Override
    public <T> T get(RestTemplate template, String url, HttpHeaders header, Object param, Class<?> collectionClass,
                     Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.GET, header, param, null, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, Object body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, new HttpHeaders(), null, body, collectionClass,
                elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, LinkedMultiValueMap body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, new HttpHeaders(), null, body, collectionClass,
                elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, HttpHeaders header, Object body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, header, null, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, HttpHeaders header, LinkedMultiValueMap body,
                      Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, header, null, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, Object param, Object body, Class<?> collectionClass,
                      Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, new HttpHeaders(), param, body, collectionClass,
                elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, Object param, LinkedMultiValueMap body,
                      Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, new HttpHeaders(), param, body, collectionClass,
                elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, HttpHeaders header, Object param, Object body,
                      Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, header, param, body, collectionClass, elementClasses);
    }

    @Override
    public <T> T post(RestTemplate template, String url, HttpHeaders header, Object param, LinkedMultiValueMap body,
                      Class<?> collectionClass, Class<?>... elementClasses) {
        return getSelf().exec(template, url, HttpMethod.POST, header, param, body, collectionClass, elementClasses);
    }
}
