package com.bidr.neo4j.po.node;

import com.bidr.kernel.utils.StringUtil;
import com.bidr.neo4j.constant.CypherSqlConstant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: Neo4jQueryNode
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/10 13:41
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Neo4jQueryNode extends Neo4jNode {
    private String name;
    private String condition;
    private String sort;

    public Neo4jQueryNode() {
        this.name = CypherSqlConstant.DEFAULT_NODE_NAME;
        this.condition = StringUtil.EMPTY;
        this.sort = StringUtil.EMPTY;
    }

}
