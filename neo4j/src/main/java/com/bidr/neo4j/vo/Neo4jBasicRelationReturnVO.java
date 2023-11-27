package com.bidr.neo4j.vo;

import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.po.relation.Neo4jRelation;
import lombok.Data;

import java.io.Serializable;

/**
 * Title: Neo4jBasicRelationReturnVO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/30 09:38
 */
@Data
public class Neo4jBasicRelationReturnVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Neo4jNode start;
    private Neo4jRelation relationship;
    private Neo4jNode end;
}
