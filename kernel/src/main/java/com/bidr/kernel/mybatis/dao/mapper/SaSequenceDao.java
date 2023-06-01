package com.bidr.kernel.mybatis.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.dao.entity.SaSequence;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Title: SaSequenceDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 16:40
 */
@Mapper
public interface SaSequenceDao extends BaseMapper<SaSequence>, MyBaseMapper<SaSequence> {
    @Select("SELECT f_nextval(#{seqName})")
    String getSeq(String seqName);

    @Update("UPDATE sa_sequence SET sa_sequence.value = 0 WHERE sequence.seq_name = #{seqName}")
    void resetSeq(String seqName);
}
