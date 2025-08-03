package com.bidr.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bidr.es.config.ElasticsearchConfig;
import com.bidr.es.dao.repository.MappingBuilder;

/**
 * Title: com.bidr.es.ElasticsearchTest
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/2 12:11
 */

public class ElasticsearchTest {
    public static void main(String[] args) {
        final String host = "10.3.4.174";
        final int port = 9200;
        final String proxyHost = "10.3.4.175";
        final int proxyPort = 65535;
        ElasticsearchClient client = ElasticsearchConfig.getElasticsearchClient(host, port, null, null, true, proxyHost, proxyPort);
        String json = MappingBuilder.toJson(ElasticsearchTestEntity.class, client._transport());
        System.out.println(json);
    }
}