package com.bidr.neo4j.repository.inf;

import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jBasicRelation;
import com.bidr.neo4j.po.relation.Neo4jSaveRelation;

/**
 * Title: Neo4jRelationshipRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:15
 */
public interface Neo4jRelationshipRepo {

    /**
     * 查询节点然后创建关系
     * 创建关系(查询开始节点和结束节点然后创造关系)
     * 注意：开始节点和结束节点以及创建的关系参数一定要存在！
     * 关系如果存在，不会重复创建
     * 因为需要返回创建条数 当前方法未做条件判断
     *
     * @param saveRelation 关系构造类
     * @return 返回创建关系的个数
     */
    int queryNodeCreateRelation(Neo4jSaveRelation saveRelation);

    /**
     * 查询节点然后创建关系
     * 创建关系(查询开始节点和结束节点然后创造关系)
     * 注意：开始节点和结束节点以及创建的关系参数一定要存在！
     * 关系如果存在，不会重复创建
     * 因为需要返回创建条数 当前方法未做条件判断
     *
     * @param start    起始节点
     * @param end      结束节点
     * @param relation 关系
     * @return 返回创建关系的个数
     */
    int queryNodeCreateRelation(Neo4jQueryNode start, Neo4jQueryNode end, Neo4jBasicRelation relation);
}
