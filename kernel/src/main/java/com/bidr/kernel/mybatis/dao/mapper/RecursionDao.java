package com.bidr.kernel.mybatis.dao.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Title: RecursionDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/10 14:54
 */
public interface RecursionDao {

    @Select("WITH RECURSIVE  temp(${idFieldName}, ${pidFieldName}) AS ( " +
            "   SELECT ${idFieldName}, ${pidFieldName} FROM ${tableName} WHERE ${idFieldName} = #{id} " +
            "UNION ALL " +
            "   SELECT t.${idFieldName}, t.${pidFieldName}  " +
            "   FROM  temp r, ${tableName} t " +
            "   WHERE t.${pidFieldName} = r.${idFieldName})  " +
            "SELECT ${idFieldName} FROM temp;")
    List<Object> getChildList(@Param("tableName") String tableName, @Param("idFieldName") String idFieldName,
                              @Param("pidFieldName") String pidFieldName, @Param("id") Object id);

    @Select("WITH RECURSIVE  temp(${idFieldName}, ${pidFieldName}) AS ( " +
            "   SELECT ${idFieldName}, ${pidFieldName} FROM ${tableName} WHERE ${idFieldName} = #{id} " +
            "UNION ALL " +
            "   SELECT t.${idFieldName}, t.${pidFieldName}  " +
            "   FROM  temp r, ${tableName} t " +
            "   WHERE t.${idFieldName} = r.${pidFieldName})  " +
            "SELECT ${pidFieldName} FROM temp WHERE ${pidFieldName} IS NOT null;")
    List<Object> getParentList(@Param("tableName") String tableName, @Param("idFieldName") String idFieldName,
                              @Param("pidFieldName") String pidFieldName, @Param("id") Object id);
}
