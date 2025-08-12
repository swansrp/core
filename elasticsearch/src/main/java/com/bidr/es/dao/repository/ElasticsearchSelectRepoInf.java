package com.bidr.es.dao.repository;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.util.ObjectBuilder;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.es.anno.EsField;
import com.bidr.es.config.EsFieldType;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
     */
    default List<Hit<T>> selectHit() {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()));
        return buildHitList(request);
    }

    /**
     * 获取实体列表
     *
     * @param request 请求
     * @return 列表
     */
    default List<Hit<T>> buildHitList(SearchRequest request) {
        try {
            SearchResponse<T> response = getClient().search(request, getEntityClass());
            return response.hits().hits();
        } catch (IOException e) {
            getLogger().error("查询失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询
     *
     * @param query 条件
     * @return 查询结果
     */
    default List<Hit<T>> selectHit(Query query) {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query));
        return buildHitList(request);
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
     */
    default Page<Hit<T>> selectHit(long currentPage, long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()));
        return buildHitPage(request, currentPage, pageSize);
    }

    /**
     * 获取实体分页
     *
     * @param request     请求
     * @param currentPage 当前页
     * @param pageSize    每页个数
     * @return 实体分页
     */
    default Page<Hit<T>> buildHitPage(SearchRequest request, long currentPage, long pageSize) {
        try {
            SearchResponse<T> response = getClient().search(request, getEntityClass());
            Page<Hit<T>> page = new Page<>(currentPage, pageSize, safeGetTotal(response));
            page.setRecords(response.hits().hits());
            return page;
        } catch (IOException e) {
            getLogger().error("查询失败", e);
            return new Page<>(currentPage, pageSize, 0);
        }

    }

    /**
     * 获取查询总量
     *
     * @param response 返回值
     * @return 总量
     */
    default long safeGetTotal(SearchResponse<?> response) {
        return Optional.ofNullable(response).map(ResponseBody::hits).map(HitsMetadata::total).map(TotalHits::value)
                .orElse(0L);
    }

    /**
     * 查询
     *
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<Hit<T>> selectHit(Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn, long currentPage,
                                   long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn));
        return buildHitPage(request, currentPage, pageSize);
    }

    /**
     * 查询
     *
     * @param query       查询条件
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<Hit<T>> selectHit(Query query, long currentPage, long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).query(query));
        return buildHitPage(request, currentPage, pageSize);
    }

    /**
     * 查询
     *
     * @param query       条件
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<Hit<T>> selectHit(Query query, Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn,
                                   long currentPage, long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn).query(query));
        return buildHitPage(request, currentPage, pageSize);
    }

    /**
     * 查询
     *
     * @return 查询结果
     */
    default List<T> select() {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()));
        return buildList(request);
    }

    /**
     * 获取实体列表
     *
     * @param request 请求
     * @return 列表
     */
    default List<T> buildList(SearchRequest request) {
        try {
            logRequest(request);
            SearchResponse<T> response = getClient().search(request, getEntityClass());
            return response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
        } catch (IOException e) {
            getLogger().error("查询失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 查询
     *
     * @param query 条件
     * @param fn    排序
     * @return 查询结果
     */
    default List<T> select(Query query, Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn) {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query).sort(fn));
        return buildList(request);
    }

    /**
     * 查询
     *
     * @param query       条件
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<T> select(Query query, Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn, long currentPage,
                           long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn).query(query));
        return buildPage(request, currentPage, pageSize);
    }

    /**
     * 获取实体分页
     *
     * @param request     请求
     * @param currentPage 当前页
     * @param pageSize    每页个数
     * @return 实体分页
     */
    default Page<T> buildPage(SearchRequest request, long currentPage, long pageSize) {
        try {
            logRequest(request);
            SearchResponse<T> response = getClient().search(request, getEntityClass());
            Page<T> page = new Page<>(currentPage, pageSize, safeGetTotal(response));
            page.setRecords(response.hits().hits().stream().map(Hit::source).collect(Collectors.toList()));
            return page;
        } catch (IOException e) {
            getLogger().error("查询失败", e);
            return new Page<>(currentPage, pageSize, 0);
        }
    }

    /**
     * 查询
     *
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<T> select(long currentPage, long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()));
        return buildPage(request, currentPage, pageSize);
    }

    /**
     * 查询
     *
     * @param fn          排序
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<T> select(Function<SortOptions.Builder, ObjectBuilder<SortOptions>> fn, long currentPage,
                           long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).sort(fn));
        return buildPage(request, currentPage, pageSize);
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

    /**
     * 获取一个text字段 都维度查询query
     *
     * @param sFunction 字段
     * @param keyword   关键词
     * @return 查询条件
     */
    default Query buildMatchQuery(SFunction<T, ?> sFunction, String keyword) {
        final Field field = LambdaUtil.getField(sFunction);
        return buildMatchQuery(field, keyword);
    }

    /**
     * 指定一个text字段 多维度查询query
     *
     * @param field   字段
     * @param keyword 关键词
     * @return 查询条件
     */
    default Query buildMatchQuery(Field field, String keyword) {
        return Query.of(q -> q.bool(b -> {
            buildFieldMatchQuery(keyword, b, field);
            return b.minimumShouldMatch("1");
        }));
    }

    /**
     * 给定字段和关键字 填充boolQuery
     *
     * @param keyword 关键字
     * @param b       boolQuery
     * @param field   字段
     */
    default void buildFieldMatchQuery(String keyword, BoolQuery.Builder b, Field field) {
        EsField anno = field.getAnnotation(EsField.class);
        if (anno != null && anno.type() == EsFieldType.TEXT) {
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
                b.should(s -> s.match(m -> m.field(field.getName() + "_" + anno.hanlpFieldSuffix()).query(keyword)));
            }
        }
    }

    /**
     * 获取一个text字段 都维度查询query
     *
     * @param fieldName 字段
     * @param keyword   关键词
     * @return 查询条件
     */
    default Query buildMatchQuery(String fieldName, String keyword) {
        final Field field = ReflectionUtil.getField(getEntityClass(), fieldName);
        return buildMatchQuery(field, keyword);
    }

    /**
     * 根据一个关键词 尽最大可能查询 结果
     *
     * @param keyword 关键词
     * @return 查询结果列表
     */
    default List<T> smartQuery(String keyword) {
        Query query = buildMatchQuery(keyword);
        return select(query);
    }

    /**
     * 全text字段 多维度查询query
     *
     * @param keyword 关键词
     * @return 查询条件
     */
    default Query buildMatchQuery(String keyword) {
        return Query.of(q -> q.bool(b -> {
            for (Field field : ReflectionUtil.getFields(getEntityClass())) {
                buildFieldMatchQuery(keyword, b, field);
            }
            return b.minimumShouldMatch("1");
        }));
    }

    /**
     * 查询
     *
     * @param query 条件
     * @return 查询结果
     */
    default List<T> select(Query query) {
        SearchRequest request = SearchRequest.of(s -> s.index(getIndexName()).query(query));
        return buildList(request);
    }

    /**
     * 根据一个关键词 尽最大可能查询 结果
     *
     * @param keyword 关键词
     * @return 查询结果列表
     */
    default List<T> smartQuery(String keyword, long pageSize) {
        Query query = buildMatchQuery(keyword);
        return select(query, 1, pageSize).getRecords();
    }

    /**
     * 查询
     *
     * @param query       查询条件
     * @param currentPage 当前页
     * @param pageSize    每页大小
     * @return 查询结果
     */
    default Page<T> select(Query query, long currentPage, long pageSize) {
        SearchRequest request = SearchRequest.of(
                s -> s.index(getIndexName()).from(Long.valueOf((pageSize - 1) * currentPage).intValue())
                        .size(Long.valueOf(pageSize).intValue()).query(query));
        return buildPage(request, currentPage, pageSize);
    }


}