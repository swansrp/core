package com.bidr.neo4j.repository.inf;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.neo4j.po.Node4jQuery;
import com.bidr.neo4j.vo.Neo4jBasicRelationReturnVO;

import java.util.List;

/**
 * Title: Neo4jPortalRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/16 10:15
 */
public interface Neo4jPortalRepo {
    /**
     * 查询关系链
     *
     * @param conditions  关系链各个位置的关系
     * @param currentPage 当前节点数
     * @param pageSize    页数
     * @return 关系
     */
    Page<Neo4jBasicRelationReturnVO> queryAdvanced(List<Node4jQuery> conditions, long currentPage,
                                                   long pageSize);
}
