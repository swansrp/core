package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.*;
import com.bidr.kernel.mybatis.anno.EnableTruncate;
import com.bidr.kernel.utils.FuncUtil;

import java.io.IOException;
import java.util.List;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchDeleteRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    /**
     * 清空数据库
     *
     * @return 结果
     * @throws IOException 异常
     */
    default DeleteByQueryResponse truncate() throws IOException {
        DeleteByQueryResponse result = null;
        EnableTruncate enableTruncate = this.getClass().getAnnotation(EnableTruncate.class);
        if (FuncUtil.isNotEmpty(enableTruncate)) {
            getLogger().warn("#### [{}] 清空数据库表 开始 ####", getIndexName());
            result = delete(QueryBuilders.matchAll(e -> e));
            getLogger().warn("#### [{}] 清空数据库表 结束 ####", getIndexName());
        } else {
            getLogger().warn("#### [{}] 不支持 清空数据库表 ####", getIndexName());
        }
        return result;
    }

    /**
     * 按条件删除
     *
     * @param query 条件
     * @return 结果
     * @throws IOException 异常
     */
    default DeleteByQueryResponse delete(Query query) throws IOException {
        DeleteByQueryRequest request = DeleteByQueryRequest.of(c -> c.index(getIndexName()).query(query)
                .conflicts(co.elastic.clients.elasticsearch._types.Conflicts.Proceed));
        logRequest(request);
        return getClient().deleteByQuery(request);
    }

    /**
     * 按id删除
     *
     * @param id id
     * @return 结果
     * @throws IOException 异常
     */
    default DeleteResponse delete(String id) throws IOException {
        DeleteRequest request = DeleteRequest.of(c -> c.index(getIndexName()).id(id));
        return getClient().delete(request);
    }

    /**
     * 按id删除
     *
     * @param doc 实体
     * @return 结果
     * @throws IOException 异常
     */
    default DeleteResponse deleteById(T doc) throws IOException {
        String id = extractId(doc);
        DeleteRequest request = DeleteRequest.of(c -> c.index(getIndexName()).id(id));
        return getClient().delete(request);
    }

    /**
     * 按id删除列表
     *
     * @param docs 实体列表
     * @return 结果
     * @throws IOException 异常
     */
    default BulkResponse deleteById(List<T> docs) throws IOException {
        List<String> savedIds = getIds(docs);
        BulkRequest.Builder br = new BulkRequest.Builder();
        for (String savedId : savedIds) {
            br.operations(op -> op.delete(d -> d.index(getIndexName()).id(savedId)));
        }
        BulkRequest request = br.build();
        logRequest(request);
        return getClient().bulk(request);
    }
}