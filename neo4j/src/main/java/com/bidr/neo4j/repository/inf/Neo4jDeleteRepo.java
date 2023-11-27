package com.bidr.neo4j.repository.inf;

import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jQueryRelation;

/**
 * Title: Neo4jDeleteRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:15
 */
public interface Neo4jDeleteRepo {


    /**
     * 删除节点和相关关系
     * 只删除不存在关系的，存在关系的节点将不会被删除关系
     *
     * @param node 节点条件 有关系的节点不会删除
     * @return 删除个数
     */
    Integer delNode(Neo4jQueryNode node);

    /**
     * 删除节点和相关关系
     *
     * @param node        节点条件
     * @param delRelation true 删除节点相关的关系；false 只删除不存在关系的，存在关系的节点将不会被删除关系
     * @return 删除个数
     */
    Integer delNode(Neo4jQueryNode node, boolean delRelation);

    /**
     * 删除关系
     *
     * @param relation 关系属性
     * @return
     */
    Integer delRelation(Neo4jQueryRelation relation);

}
