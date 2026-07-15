package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.FormWideTableSyncLog;
import com.bidr.forge.dao.mapper.FormWideTableSyncLogMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 宽表同步日志 Repository Service
 *
 * @author sharp
 */
@Service
public class FormWideTableSyncLogService extends BaseSqlRepo<FormWideTableSyncLogMapper, FormWideTableSyncLog> {
}
