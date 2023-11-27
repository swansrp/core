package com.bidr.neo4j.po.relation;

import com.bidr.neo4j.po.node.Neo4jQueryNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: Neo4jQueryRelation
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/10 11:38
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Neo4jQueryRelation extends Neo4jBasicRelation {
    private Neo4jQueryNode start;
    private Neo4jQueryNode end;
    private List<Neo4jQueryNode> extraNodeList;
    private String relationCondition;
    private String level;
}
