package com.bidr.framework.config.rest;

import com.bidr.kernel.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

/**
 * Title: RestTemplateConfig
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:13
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${my.rest.proxy.enable}")
    private boolean proxyEnable;
    @Value("${my.rest.proxy.host}")
    private String proxyHost;
    @Value("${my.rest.proxy.port}")
    private String proxyPort;
    @Value("${my.rest.config.request.timeout}")
    private int requestTimeout;
    @Value("${my.rest.config.connect.timeout}")
    private int connectTimeout;
    @Value("${my.rest.config.read.timeout}")
    private int readTimeout;
    private ResponseErrorHandler responseErrorHandler = new ResponseErrorHandler() {

        @Override
        public boolean hasError(ClientHttpResponse clientHttpResponse) throws IOException {
            return !clientHttpResponse.getStatusCode().equals(HttpStatus.OK);
        }

        @Override
        public void handleError(ClientHttpResponse clientHttpResponse) throws IOException {
            String repErrorString = StreamUtils.copyToString(clientHttpResponse.getBody(), StandardCharsets.UTF_8);
            log.info("code = {} resp = {}", clientHttpResponse.getStatusCode(), repErrorString);
            throw new ServiceException(repErrorString);
        }
    };

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectTimeout(connectTimeout);
        httpRequestFactory.setReadTimeout(readTimeout);

        RestTemplate restTemplate = new RestTemplate(httpRequestFactory);
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter =
                new MappingJackson2HttpMessageConverter();
        mappingJackson2HttpMessageConverter
                .setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN));
        restTemplate.getMessageConverters().add(1, mappingJackson2HttpMessageConverter);
        restTemplate.setInterceptors(Collections.singletonList(new AgentInterceptor()));
        restTemplate.setErrorHandler(responseErrorHandler);
        return restTemplate;
    }

    public class AgentInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
                throws IOException {
            HttpHeaders headers = request.getHeaders();
            if (headers.getContentType() == null) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            return execution.execute(request, body);
        }
    }

}
