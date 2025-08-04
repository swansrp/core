package com.bidr.es.dao.repository;

/**
 * Title: ElasticsearchQueryRepoInf
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/3 13:47
 */

public class ElasticsearchQueryRepoInf {

//    private final List<Query> mustQueries = new ArrayList<>();
//    private final List<Query> shouldQueries = new ArrayList<>();
//    private final List<Query> mustNotQueries = new ArrayList<>();
//    private final List<SortOptions> sortOptions = new ArrayList<>();
//    private int from = 0;
//    private int size = 10;
//
//    // match 查询
//    public ElasticsearchQueryRepoInf mustMatch(String field, String value) {
//        mustQueries.add(Query.of(q -> q.match(m -> m.field(field).query(value))));
//        return this;
//    }
//
//    // term 查询
//    public ElasticsearchQueryRepoInf term(String field, String value) {
//        mustQueries.add(Query.of(q -> q.term(t -> t.field(field).value(value))));
//        return this;
//    }
//
//    // range 查询（字符串）
//    public ElasticsearchQueryRepoInf range(String field, String gte, String lte) {
//        mustQueries.add(Query.of(q -> q.range(
//                RangeQuery.of(r -> r
//                        .field(field)
//                        .gte(JsonData.of(gte))
//                        .lte(JsonData.of(lte))
//                )
//        )));
//        return this;
//        return this;
//    }
//
//    // 多字段 multi_match 查询
//    public ElasticsearchQueryRepoInf multiMatch(String queryText, List<String> fields) {
//        shouldQueries.add(Query.of(q -> q.multiMatch(m -> m.query(queryText).fields(fields))));
//        return this;
//    }
//
//    // 排序
//    public ElasticsearchQueryRepoInf sort(String field, String order) {
//        sortOptions.add(SortOptions.of(s -> s.field(f -> f.field(field).order("desc".equalsIgnoreCase(order) ? SortOrder.Desc : SortOrder.Asc))));
//        return this;
//    }
//
//    public ElasticsearchQueryRepoInf from(int from) {
//        this.from = from;
//        return this;
//    }
//
//    default ElasticsearchQueryRepoInf size(int size) {
//        this.size = size;
//        return this;
//    }
//
//    // 构建最终的查询体
//    default SearchRequest.Builder build(String index) {
//        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
//        if (!mustQueries.isEmpty()) boolBuilder.must(mustQueries);
//        if (!shouldQueries.isEmpty()) boolBuilder.should(shouldQueries);
//        if (!mustNotQueries.isEmpty()) boolBuilder.mustNot(mustNotQueries);
//
//        return new SearchRequest.Builder()
//                .index(index)
//                .query(Query.of(q -> q.bool(boolBuilder.build())))
//                .from(from)
//                .size(size)
//                .sort(sortOptions);
//    }
}