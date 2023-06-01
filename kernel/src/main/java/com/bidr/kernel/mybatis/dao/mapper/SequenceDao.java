package com.bidr.kernel.mybatis.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.dao.entity.Sequence;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Title: SequenceDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/01 13:32
 */
@Mapper
public interface SequenceDao extends BaseMapper<Sequence>, MyBaseMapper<Sequence> {
    @Select("SELECT f_nextval(#{seqName})")
    String getSeq(String seqName);

    @Update("UPDATE sequence SET sequence.value = 0 WHERE sequence.seq_name = #{seqName}")
    void resetSeq(String seqName);
}