package com.bidr.neo4j.repository.inf;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Title: Neo4jCommonRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:18
 */
@Component
public interface Neo4jCommonRepo {
    /**
     * 获取所有的标签名称
     *
     * @return 标签名称
     */
    List<String> getAllLabelName();

    /**
     * 获取所有的关系名称
     *
     * @return 关系名称
     */
    List<String> getAllRelationName();
}
