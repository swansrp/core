package com.bidr.neo4j.repository.inf;

import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jSaveRelation;

import java.util.List;

/**
 * Title: Neo4jInsertRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:15
 */
public interface Neo4jInsertRepo {

    /**
     * 创建节点(不去重)
     *
     * @param node 节点
     * @param
     * @return
     */
    int insertNode(Neo4jNode node);

    /**
     * 创建节点
     *
     * @param node  节点
     * @param noDup 是否去重。 true去重 false不去重
     * @return 创建个数
     */
    int insertNode(Neo4jNode node, Boolean noDup);

    /**
     * 创建节点，（去重增强型）
     * 创建节点，如果节点存在，先把它删除，在重新创建
     * 这个方法的目的是因为 createNode方法所谓的去重，是指如果 ，已有节点A,需要创建的节点B,如果A的属性个数大于B的属性且属性对应的值一模一样，就会创建一个新的A。所以现在的方式是对B新增A中B缺少的属性
     *
     * @param node
     * @return
     */
    int insertOrUpdateNode(Neo4jNode node);

    /**
     * 创建节点，（去重增强型）
     * 创建节点，如果节点存在，先把它删除，在重新创建
     * 这个方法的目的是因为 createNode方法所谓的去重，是指如果 ，已有节点A,需要创建的节点B,如果A的属性个数大于B的属性且属性对应的值一模一样，就会创建一个新的A。所以现在的方式是对B新增A中B缺少的属性
     *
     * @param node
     * @param queryNode 查询条件
     * @return
     */
    int insertOrUpdateNode(Neo4jNode node, Neo4jQueryNode queryNode);

    /**
     * 批量创建节点(存在的节点将会被重复创建)
     *
     * @param nodeList 节点的list集合
     * @return 创建成功条数
     */
    int batchInsertNode(List<Neo4jNode> nodeList);

    /**
     * 批量创建节点
     *
     * @param nodeList 节点的list集合
     * @param noDup    是否去重。 true去重（存在的节点将不会被创建） false不去重
     * @return 创建成功条数
     */
    int batchInsertNode(List<Neo4jNode> nodeList, Boolean noDup);

    /**
     * 创建节点同时创建关系
     * 重复的不会被创建
     *
     * @param saveRelation
     * @return
     */
    boolean insertNodeAndRelation(Neo4jSaveRelation saveRelation);
}
