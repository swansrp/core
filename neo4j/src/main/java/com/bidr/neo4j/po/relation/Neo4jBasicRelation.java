package com.bidr.neo4j.po.relation;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Title: Neo4jBasicRelation
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/10 14:02
 */
@Data
public class Neo4jBasicRelation {
    /**
     * id
     */
    private Long id;
    /**
     * 关系类型
     */
    private String type;
    /**
     * 标签属性
     */
    private Map<String, Object> property = new HashMap<>();
}
