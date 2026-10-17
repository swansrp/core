package com.bidr.td.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.td.dao.entity.TdTagMapping;
import com.bidr.td.dao.mapper.TdTagMappingDao;
import org.springframework.stereotype.Repository;

@Repository
public class TdTagMappingService extends BaseSqlRepo<TdTagMappingDao, TdTagMapping> {
}
