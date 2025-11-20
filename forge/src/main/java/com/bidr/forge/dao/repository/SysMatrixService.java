package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.mapper.SysMatrixMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 矩阵配置Repository
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysMatrixService extends BaseSqlRepo<SysMatrixMapper, SysMatrix> {
}
