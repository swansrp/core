package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.SysMatrixChangeLog;
import com.bidr.forge.dao.mapper.SysMatrixChangeLogMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 矩阵表结构变更日志 Repository
 *
 * @author sharp
 * @since 2025-11-21
 */
@Service
public class SysMatrixChangeLogService extends BaseSqlRepo<SysMatrixChangeLogMapper, SysMatrixChangeLog> {
}
