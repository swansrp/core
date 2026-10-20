package com.bidr.blob.dao.mapper;

import com.bidr.blob.dao.entity.KbBlob;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;

/**
 * 内容块（跨空间去重，不带空间维度）数据访问
 *
 * @author sharp
 */
public interface KbBlobMapper extends MyBaseMapper<KbBlob> {
}
