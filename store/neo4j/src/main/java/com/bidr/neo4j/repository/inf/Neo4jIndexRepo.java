package com.bidr.neo4j.repository.inf;

/**
 * Title: Neo4jIndexRepo
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/23 10:10
 */
public interface Neo4jIndexRepo {
    /**
     * 添加索引
     *
     * @param label
     * @param property
     */
    void createIndex(String label, String property);

    /**
     * 删除索引
     *
     * @param label
     * @param property
     */

    void dropIndex(String label, String property);
}
