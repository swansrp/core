package com.bidr.es.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: ElasticsearchConfig
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/1 14:05
 */

@Configuration
public class ElasticsearchConfig {
    @Value("${my.elasticsearch.username:}")
    private String username;
    @Value("${my.elasticsearch.password:}")
    private String password;
    @Value("${my.elasticsearch.host}")
    private String host;
    @Value("${my.elasticsearch.port}")
    private int port;
    @Value("${my.elasticsearch.proxy.enable:false}")
    private boolean proxyEnable;
    @Value("${my.elasticsearch.proxy.host:}")
    private String proxyHost;
    @Value("${my.elasticsearch.proxy.port:}")
    private int proxyPort;

    public static ElasticsearchClient getElasticsearchClient(String host, Integer port, String username,
                                                             String password, Boolean proxyEnable, String proxyHost,
                                                             Integer proxyPort) {
        RestClient restClient = getRestClient(host, port, username, password, proxyEnable, proxyHost, proxyPort);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
        RestClientTransport transport = new RestClientTransport(restClient, jsonpMapper);
        return new ElasticsearchClient(transport);
    }

    public static RestClient getRestClient(String host, Integer port, String username, String password,
                                           Boolean proxyEnable, String proxyHost, Integer proxyPort) {
        // 构建 RestClientBuilder
        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    if (FuncUtil.isNotEmpty(username) && FuncUtil.isNotEmpty(password)) {
                        // 设置账号密码
                        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    if (proxyEnable) {
                        // 设置代理
                        httpClientBuilder.setProxy(new HttpHost(proxyHost, proxyPort));
                    }
                    return httpClientBuilder;
                });

        return builder.build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        return ElasticsearchConfig.getElasticsearchClient(host, port, username, password, proxyEnable, proxyHost,
                proxyPort);
    }
}
