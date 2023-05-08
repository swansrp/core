/**
 * Title: SequenceDao.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019-7-30 13:36
 * @description Project Name: Grote
 * @Package: com.srct.service.dao.mapper
 */
package com.bidr.kernel.mybatis.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SequenceDao {

    @Select("SELECT f_nextval(#{seqName})")
    String getSeq(String seqName);

    @Update("UPDATE sequence SET sequence.value = 0 WHERE sequence.seq_name = #{seqName}")
    void resetSeq(String seqName);

}
