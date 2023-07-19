package com.bidr.kernel.mybatis.dao.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * Title: CommonMapper
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/19 08:39
 */
public interface CommonMapper {

    /**
     * 清空表 慎用
     *
     * @param tableName
     */
    @Update("truncate table ${tableName}")
    void truncate(@Param("tableName") String tableName);
}
