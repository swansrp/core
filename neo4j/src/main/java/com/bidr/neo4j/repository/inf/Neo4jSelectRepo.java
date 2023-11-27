package com.bidr.neo4j.repository.inf;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.neo4j.po.node.Neo4jNode;
import com.bidr.neo4j.po.node.Neo4jQueryNode;
import com.bidr.neo4j.po.relation.Neo4jQueryRelation;
import com.bidr.neo4j.vo.Neo4jBasicRelationReturnVO;

import java.util.List;

/**
 * Title: Neo4jSelectRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:15
 */
public interface Neo4jSelectRepo {
    /**
     * 按条件查询节点
     *
     * @param node
     * @return 返回节点集合
     */
    List<Neo4jNode> queryNode(Neo4jQueryNode node);


    /**
     * 按条件分页查询节点列表
     *
     * @param node        条件
     * @param currentPage 当前页码
     * @param pageSize    每页个数
     * @return 返回节点列表
     */
    Page<Neo4jNode> queryNode(Neo4jQueryNode node, long currentPage, long pageSize);

    /**
     * 查询关系
     *
     * @param relation 条件
     * @return 返回节点列表
     */
    List<Neo4jBasicRelationReturnVO> queryRelation(Neo4jQueryRelation relation);

    /**
     * 按条件分页查询关系
     *
     * @param relation 条件
     * @param currentPage 当前页码
     * @param pageSize    每页个数
     * @return 返回节点列表
     */
    Page<Neo4jBasicRelationReturnVO> queryRelation(Neo4jQueryRelation relation, long currentPage, long pageSize);
}
