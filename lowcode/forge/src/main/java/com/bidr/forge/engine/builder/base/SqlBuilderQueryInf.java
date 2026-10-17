package com.bidr.forge.engine.builder.base;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;

import java.util.Map;

/**
 * SQL构建器查询接口
 * 定义查询SQL构建方法
 *
 * @author Sharp
 * @since 2025-11-27
 */
public interface SqlBuilderQueryInf extends SqlBuilderBaseInf {

    /**
     * 构建SELECT查询SQL
     *
     * @param req        高级查询请求
     * @param aliasMap   字段别名映射
     * @param parameters 参数Map（输出参数）
     * @return SELECT SQL
     */
    String buildSelect(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters);

    /**
     * 构建COUNT查询SQL
     *
     * @param req        高级查询请求
     * @param aliasMap   字段别名映射
     * @param parameters 参数Map（输出参数）
     * @return COUNT SQL
     */
    String buildCount(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters);

    /**
     * 构建可复用的SQL片段容器（优化分页查询，避免重复解析）
     *
     * @param req        高级查询请求
     * @param aliasMap   字段别名映射
     * @param parameters 参数Map（输出参数）
     * @return SQL片段容器
     */
    default SqlParts buildSqlParts(AdvancedQueryReq req, Map<String, String> aliasMap, Map<String, Object> parameters) {
        // 默认实现：调用原方法（子类可重写优化）
        return new SqlParts(
                buildSelect(req, aliasMap, parameters),
                buildCount(req, aliasMap, new java.util.HashMap<>(parameters))
        );
    }

    /**
     * SQL片段容器（用于分页查询优化）
     */
    class SqlParts {
        private final String selectSql;
        private final String countSql;

        public SqlParts(String selectSql, String countSql) {
            this.selectSql = selectSql;
            this.countSql = countSql;
        }

        public String getSelectSql() {
            return selectSql;
        }

        public String getCountSql() {
            return countSql;
        }
    }
}
