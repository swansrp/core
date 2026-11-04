package com.bidr.kernel.mybatis.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: SyncOutboxMapper
 * Description: 变更事件发件箱 mapper（T3.3a）——无自定义 SQL，读走 wrapper、清理走 {@code delete(wrapper)}。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Mapper
public interface SyncOutboxMapper extends BaseMapper<SyncOutbox>, MyBaseMapper<SyncOutbox> {
}