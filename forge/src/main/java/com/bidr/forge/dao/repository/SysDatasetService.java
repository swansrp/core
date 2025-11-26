package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.mapper.SysDatasetMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 数据集主表 Repository Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
public class SysDatasetService extends BaseSqlRepo<SysDatasetMapper, SysDataset> {
}
