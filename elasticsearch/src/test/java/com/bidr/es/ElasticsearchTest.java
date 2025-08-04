package com.bidr.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import com.bidr.es.anno.EsIndex;
import com.bidr.es.config.ElasticsearchConfig;
import com.bidr.es.config.ElasticsearchMappingConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Title: com.bidr.es.ElasticsearchTest
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/2 12:11
 */
@Slf4j
public class ElasticsearchTest {
    public static void main(String[] args) throws IOException {
        final String host = "10.3.4.174";
        final int port = 9200;
        final String proxyHost = "10.3.4.175";
        final int proxyPort = 65535;
        RestClient restClient = ElasticsearchConfig.getRestClient(host, port, null, null, true, proxyHost, proxyPort);
        ElasticsearchClient client = ElasticsearchConfig.getElasticsearchClient(host, port, null, null, true, proxyHost,
                proxyPort);
        mappingTest(client, ElasticsearchTestEntity.class);
        healthTest(client);
        getAnalyserTest(restClient);
        aliasExistTest(client, ElasticsearchTestEntity.class);
        ElasticsearchTestRepo repo = new ElasticsearchTestRepo();
        repo.init();
    }

    public static void mappingTest(ElasticsearchClient client, Class<?> clazz) {
        String json = ElasticsearchMappingConfig.toJson(clazz);
        log.info(json);
    }

    public static void healthTest(ElasticsearchClient client) throws IOException {
        HealthResponse healthResponse = client.cluster().health();
        log.info("Cluster Health: {}", healthResponse.status());
    }

    public static void getAnalyserTest(RestClient client) throws IOException {
        // 使用底层 RestClient 执行 cat/plugins API
        Request request = new Request("GET", "/_cat/plugins?v");
        Response response = client.performRequest(request);

        // 打印插件信息
        InputStream is = response.getEntity().getContent();
        String body = IOUtils.toString(is, StandardCharsets.UTF_8);
        log.info(body);
    }

    public static void aliasExistTest(ElasticsearchClient client, Class<?> clazz) throws IOException {
        String aliasName = clazz.getAnnotation(EsIndex.class).name();
        boolean aliasExists = client.indices().exists(b -> b.index(aliasName)).value();
        log.info("{}", aliasExists);
    }

    public static void createIndexTest(ElasticsearchClient client, Class<?> clazz) throws IOException {
        EsIndex esIndex = clazz.getAnnotation(EsIndex.class);
        String aliasName = esIndex.name();
        String indexName = aliasName + "_" + System.currentTimeMillis();
        Map<String, Property> mapping = ElasticsearchMappingConfig.buildMapping(clazz);
        client.indices().create(c -> c.index(indexName).settings(ElasticsearchMappingConfig.buildIndexSettings(clazz))
                .mappings(m -> m.properties(mapping)));
        log.info("创建索引：{}", indexName);
    }
}