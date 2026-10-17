package com.bidr.neo4j.po.node;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: Neo4jBasicNode
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/30 09:36
 */
@Data
public class Neo4jNode implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * id
     */
    private Long id;

    /**
     * 标签
     */
    private List<String> labels = new ArrayList<>();

    /**
     * 标签属性
     */
    private Map<String, Object> property = new HashMap<>();

}
