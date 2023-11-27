package com.bidr.neo4j.po.relation;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Title: Neo4jQueryRelation
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/30 09:37
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Neo4jRelation extends Neo4jBasicRelation implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 开始节点id
     */
    private Long start;
    /**
     * 结束节点id
     */
    private Long end;
}
