package com.bidr.es.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.bidr.kernel.utils.FuncUtil;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
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
    @Value("${my.elasticsearch.proxy.host:}")
    private String proxyHost;
    @Value("${my.elasticsearch.proxy.port:}")
    private int proxyPort;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(new HttpHost(host, port, "http"))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    // 配置认证
                    if (FuncUtil.isNotEmpty(username)) {
                        // 创建凭证提供器
                        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                        credentialsProvider.setCredentials(AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));
                        httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                    }
                    // 配置代理
                    if (FuncUtil.isNotEmpty(proxyHost)) {
                        HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                        httpClientBuilder.setProxy(proxy);
                    }
                    return httpClientBuilder;
                }).build();

        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
