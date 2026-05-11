package com.bidr.td.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.td.dao.entity.TdSyncLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TdSyncLogDao extends BaseMapper<TdSyncLog>, MyBaseMapper<TdSyncLog> {
}
