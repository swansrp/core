package com.bidr.neo4j.repository.inf;

import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jQueryRelation;
import com.bidr.neo4j.vo.RelationVO;

/**
 * Title: Neo4jCountRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:15
 */
public interface Neo4jCountRepo {

    /**
     * 按条件查询节点个数
     *
     * @param node 节点类型
     * @return 返回节点个数
     */
    Long countNode(Neo4jQueryNode node);

    /**
     * 按条件查询关系个数
     *
     * @param relationDTO 关系
     * @return 返回节点个数
     */
    Long countRelation(Neo4jQueryRelation relationDTO);

    /**
     * 按条件查询关系个数
     *
     * @param relationVO 关系
     * @return 返回节点个数
     */
    Long countRelation(RelationVO relationVO);
}
