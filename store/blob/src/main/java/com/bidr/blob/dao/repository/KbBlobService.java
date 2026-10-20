package com.bidr.blob.dao.repository;

import com.bidr.blob.dao.entity.KbBlob;
import com.bidr.blob.dao.mapper.KbBlobMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 内容块（跨空间去重，不带空间维度）数据仓库（仅业务查询方法，DDL 见 Schema）
 *
 * @author sharp
 */
@Service
public class KbBlobService extends BaseSqlRepo<KbBlobMapper, KbBlob> {
}
