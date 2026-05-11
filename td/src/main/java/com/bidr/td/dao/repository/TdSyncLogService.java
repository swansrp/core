package com.bidr.td.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.td.dao.entity.TdSyncLog;
import com.bidr.td.dao.mapper.TdSyncLogDao;
import org.springframework.stereotype.Repository;

@Repository
public class TdSyncLogService extends BaseSqlRepo<TdSyncLogDao, TdSyncLog> {
}
