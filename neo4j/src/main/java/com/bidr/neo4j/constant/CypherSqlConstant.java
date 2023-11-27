package com.bidr.neo4j.constant;

/**
 * Title: CypherSQLConstant
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 13:40
 */
public class CypherSqlConstant {
    public static final String ALL_LABEL_NAME_FIELD_NAME = "name";
    public static final String GET_ALL_LABEL_NAME_SQL =
            "MATCH (n) RETURN distinct labels(n) as " + ALL_LABEL_NAME_FIELD_NAME;

    public static final String ALL_RELATIONSHIP_NAME_FIELD_NAME = "name";
    public static final String GET_ALL_RELATIONSHIP_NAME_SQL =
            "MATCH ()-[r]-() RETURN distinct type(r) as " + ALL_RELATIONSHIP_NAME_FIELD_NAME;

    public static final String DEFAULT_NODE_NAME = "n";

    public static final String DEFAULT_START_NODE_NAME = "start";
    public static final String DEFAULT_END_NODE_NAME = "end";
    public static final String DEFAULT_RELATIONSHIP_NAME = "r";
}
