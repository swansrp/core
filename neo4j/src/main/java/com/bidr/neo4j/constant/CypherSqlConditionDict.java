package com.bidr.neo4j.constant;

import cn.hutool.core.util.EnumUtil;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.neo4j.repository.query.Neo4jConditionBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: CypherSqlConditionDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/09 14:11
 */
@Getter
@AllArgsConstructor
public enum CypherSqlConditionDict implements Dict {
    /**
     * Cypher SQL 关系字典
     */

    EQUAL(PortalConditionDict.EQUAL.getValue(), "%s = %s", CypherSqlConditionDictInjector.oneParma),
    NOT_EQUAL(PortalConditionDict.NOT_EQUAL.getValue(), "%s <> %s", CypherSqlConditionDictInjector.oneParma),
    GREATER(PortalConditionDict.GREATER.getValue(), "%s > %s", CypherSqlConditionDictInjector.oneParma),
    GREATER_EQUAL(PortalConditionDict.GREATER_EQUAL.getValue(), "%s >= %s", CypherSqlConditionDictInjector.oneParma),
    LESS(PortalConditionDict.LESS.getValue(), "%s < %s", CypherSqlConditionDictInjector.oneParma),
    LESS_EQUAL(PortalConditionDict.LESS_EQUAL.getValue(), "%s <= %s", CypherSqlConditionDictInjector.oneParma),
    NULL(PortalConditionDict.NULL.getValue(), "%s is null ", CypherSqlConditionDictInjector.noneParma),
    NOT_NULL(PortalConditionDict.NOT_NULL.getValue(), "%s is not null", CypherSqlConditionDictInjector.noneParma),
    LIKE(PortalConditionDict.LIKE.getValue(), "%s contains %s", CypherSqlConditionDictInjector.oneStringParma),
    NOT_LIKE(PortalConditionDict.NOT_LIKE.getValue(), " not (%s contains %s) ",
            CypherSqlConditionDictInjector.oneStringParma),
    IN(PortalConditionDict.IN.getValue(), "%s in [%s] ", CypherSqlConditionDictInjector.multiParma),
    NOT_IN(PortalConditionDict.NOT_IN.getValue(), " not (%s  in [%s]) ", CypherSqlConditionDictInjector.multiParma),
    BETWEEN(PortalConditionDict.BETWEEN.getValue(), " %s >= %s AND %s <= %s ", CypherSqlConditionDictInjector.twoParma),
    NOT_BETWEEN(PortalConditionDict.NOT_BETWEEN.getValue(), " %s < %s AND %s > %s ",
            CypherSqlConditionDictInjector.twoParma);


    private final Integer value;
    private final String label;
    private final Neo4jConditionBuilder inf;

    public static CypherSqlConditionDict of(Integer value) {
        return EnumUtil.getBy(CypherSqlConditionDict::getValue, value);
    }

    private static class CypherSqlConditionDictInjector {
        private static final Neo4jConditionBuilder oneParma = new OneParamBuilder();
        private static final Neo4jConditionBuilder twoParma = new TwoParamBuilder();
        private static final Neo4jConditionBuilder noneParma = new NoneParamBuilder();

        private static final Neo4jConditionBuilder multiParma = new MultiParamBuilder();

        private static final Neo4jConditionBuilder oneStringParma = new OneStringParamBuilder();

    }

    private static class NoneParamBuilder implements Neo4jConditionBuilder {

        @Override
        public String build(String name, ConditionVO condition, String relationFormat) {
            StringBuffer conditionStr = new StringBuffer(" AND ");
            String columnName = name + "." + condition.getProperty();
            conditionStr.append(String.format(relationFormat, columnName));
            return conditionStr.toString();
        }
    }

    private static class OneParamBuilder implements Neo4jConditionBuilder {

        @Override
        public String build(String name, ConditionVO condition, String relationFormat) {
            StringBuilder conditionStr = new StringBuilder();
            if (FuncUtil.isNotEmpty(condition.getValue())) {
                String columnName = name + "." + condition.getProperty();
                StringBuilder param = buildValue(condition.getValue().get(0));
                conditionStr.append(String.format(relationFormat, columnName, param));
            } else {
                conditionStr.append(" 1=1 ");
            }
            return conditionStr.toString();
        }
    }

    private static class TwoParamBuilder implements Neo4jConditionBuilder {

        @Override
        public String build(String name, ConditionVO condition, String relationFormat) {
            StringBuilder conditionStr = new StringBuilder();
            if (FuncUtil.isNotEmpty(condition.getValue())) {
                Validator.assertTrue(condition.getValue().size() > 1, ErrCodeSys.PA_PARAM_FORMAT, "参数");
                String columnName = name + "." + condition.getProperty();
                StringBuilder param1 = buildValue(condition.getValue().get(0));
                StringBuilder param2 = buildValue(condition.getValue().get(1));
                conditionStr.append(String.format(relationFormat, columnName, param1, columnName, param2));
            } else {
                conditionStr.append(" 1=1 ");
            }
            return conditionStr.toString();
        }
    }

    private static class MultiParamBuilder implements Neo4jConditionBuilder {

        @Override
        public String build(String name, ConditionVO condition, String relationFormat) {
            StringBuilder conditionStr = new StringBuilder();
            if (FuncUtil.isNotEmpty(condition.getValue())) {
                String columnName = name + "." + condition.getProperty();
                StringBuilder valueStr = new StringBuilder();
                for (Object value : condition.getValue()) {
                    valueStr.append(buildValue(value));
                    valueStr.append(", ");
                }
                valueStr.deleteCharAt(valueStr.length() - 2);
                conditionStr.append(String.format(relationFormat, columnName, valueStr));
            } else {
                conditionStr.append(" 1=1 ");
            }
            return conditionStr.toString();
        }
    }

    private static class OneStringParamBuilder implements Neo4jConditionBuilder {

        @Override
        public String build(String name, ConditionVO condition, String relationFormat) {
            StringBuilder conditionStr = new StringBuilder();
            if (FuncUtil.isNotEmpty(condition.getValue())) {
                String columnName = name + "." + condition.getProperty();
                String param1 = "'" + condition.getValue().get(0) + "'";
                conditionStr.append(String.format(relationFormat, columnName, param1));
            } else {
                conditionStr.append(" 1=1 ");
            }
            return conditionStr.toString();
        }
    }
}

