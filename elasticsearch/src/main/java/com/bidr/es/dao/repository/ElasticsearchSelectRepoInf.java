package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.util.ObjectBuilder;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.es.anno.EsField;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.LambdaUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Title: ElasticsearchSelectRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public interface ElasticsearchSelectRepoInf<T> extends ElasticsearchBaseRepoInf<T> {
    /**
     * 查询
     *
     * @return 查询结果
     * @throws IOException 异常
     */
    default List<Hit<T>> selectHit() throws IOException {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        return response.hits().hits();
    }

    /**
     * 查询
     *
     * @param query 条件
     * @return 查询结果
     * @throws IOException 异常
     */
    default List<Hit<T>> selectHit(Query query) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        return response.hits().hits();
    }

    /**
     * 查询
     *
     * @param query 条件
     * @param fn    排序
     * @return 查询结果
     * @throws IOException 异常
     */
    default List<Hit<T>> selectHit(Query query,
                                   Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query).sort(fn));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        return response.hits().hits();
    }

    /**
     * 查询
     *
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     * @throws IOException 异常
     */
    default Page<Hit<T>> selectHit(long currentPage, long pageSize) throws IOException {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        Page<Hit<T>> page = new Page<>(currentPage, pageSize, response.hits().total().value());
        page.setRecords(response.hits().hits());
        return page;
    }

    /**
     * 查询
     *
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     * @throws IOException 异常
     */
    default Page<Hit<T>> selectHit(Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn, long currentPage,
                                   long pageSize) throws IOException {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        Page<Hit<T>> page = new Page<>(currentPage, pageSize, response.hits().total().value());
        page.setRecords(response.hits().hits());
        return page;
    }

    /**
     * 查询
     *
     * @param query       条件
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     * @throws IOException 异常
     */
    default Page<Hit<T>> selectHit(Query query, Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn,
                                   long currentPage, long pageSize) throws IOException {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn).query(query));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        Page<Hit<T>> page = new Page<>(currentPage, pageSize, response.hits().total().value());
        page.setRecords(response.hits().hits());
        return page;
    }

    /**
     * 查询
     *
     * @return 查询结果
     * @throws IOException 异常
     */
    default List<T> select() throws IOException {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
    }

    /**
     * 查询
     *
     * @param query 条件
     * @return 查询结果
     * @throws IOException 异常
     */
    default List<T> select(Query query) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query));
        SearchResponse<Map> response = getClient().search(request, Map.class);
        return JsonUtil.readJson(response.hits().hits().stream().map(Hit::source).collect(Collectors.toList()),
                List.class, getEntityClass());
    }

    /**
     * 查询
     *
     * @param query 条件
     * @param fn    排序
     * @return 查询结果
     * @throws IOException 异常
     */
    default List<T> select(Query query,
                           Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn) throws IOException {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query).sort(fn));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
    }

    /**
     * 查询
     *
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     * @throws IOException 异常
     */
    default Page<T> select(long currentPage, long pageSize) throws IOException {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        Page<T> page = new Page<>(currentPage, pageSize, response.hits().total().value());
        page.setRecords(response.hits().hits().stream().map(Hit::source).collect(Collectors.toList()));
        return page;
    }

    /**
     * 查询
     *
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     * @throws IOException 异常
     */
    default Page<T> select(Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn, long currentPage,
                           long pageSize) throws IOException {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        Page<T> page = new Page<>(currentPage, pageSize, response.hits().total().value());
        page.setRecords(response.hits().hits().stream().map(Hit::source).collect(Collectors.toList()));
        return page;
    }

    /**
     * 查询
     *
     * @param query       条件
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     * @throws IOException 异常
     */
    default Page<T> select(Query query, Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn, long currentPage,
                           long pageSize) throws IOException {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn).query(query));
        SearchResponse<T> response = getClient().search(request, getEntityClass());
        Page<T> page = new Page<>(currentPage, pageSize, response.hits().total().value());
        page.setRecords(response.hits().hits().stream().map(Hit::source).collect(Collectors.toList()));
        return page;
    }

    /**
     * 通过id查询
     *
     * @param doc 实体
     * @return 查询结果
     * @throws IOException 异常
     */
    default T selectById(T doc) throws IOException {
        String id = extractId(doc);
        return selectById(id);
    }

    /**
     * 通过id查询
     *
     * @param id id
     * @return 查询结果
     * @throws IOException 异常
     */
    default T selectById(String id) throws IOException {
        GetRequest.Builder builder = new GetRequest.Builder().index(getIndexName()).id(id);
        return getClient().get(builder.build(), getEntityClass()).source();
    }

    default Query buildMatchQuery(SFunction<T, ?> sFunction, String keyword) {
        return Query.of(q -> q.bool(b -> {
            Field field = LambdaUtil.getField(sFunction);
            EsField anno = field.getAnnotation(EsField.class);
            if (anno.useIk()) {
                b.should(s -> s.match(m -> m.field(field.getName() + "." + anno.ikFieldSuffix()).query(keyword)));
            }
            if (anno.usePinyin()) {
                b.should(s -> s.match(m -> m.field(field.getName() + "." + anno.pinyinFieldSuffix()).query(keyword)));
            }
            if (anno.useStConvert()) {
                b.should(
                        s -> s.match(m -> m.field(field.getName() + "." + anno.stConvertFieldSuffix()).query(keyword)));
            }
            if (anno.useHanLP()) {
                b.should(s -> s.match(m -> m.field(field.getName() + "_" + anno.useHanLP()).query(keyword)));
            }
            return b.minimumShouldMatch("1");
        }));
    }
}