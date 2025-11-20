package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.mapper.SysMatrixColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 矩阵字段配置Repository
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysMatrixColumnService extends BaseSqlRepo<SysMatrixColumnMapper, SysMatrixColumn> {
}
