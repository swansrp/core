package com.bidr.neo4j.repository.query;

import com.bidr.kernel.vo.portal.ConditionVO;

/**
 * Title: Neo4jConditionBuilder
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/09 15:15
 */
public interface Neo4jConditionBuilder {

    /**
     * 生成CypherSql 条件
     *
     * @param name           节点名称
     * @param condition      条件
     * @param relationFormat 语句格式
     * @return 条件语句
     */
    String build(String name, ConditionVO condition, String relationFormat);

    /**
     *
     * @param value 内容
     * @return
     */
    default StringBuilder buildValue(Object value) {
        StringBuilder valueStr = new StringBuilder();
        if (value instanceof String) {
            valueStr.append("'");
            valueStr.append(value);
            valueStr.append("'");
        } else {
            valueStr.append(value);
        }
        return valueStr;
    }
}
