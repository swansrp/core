package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.SysFormConfig;
import com.bidr.forge.dao.mapper.SysFormConfigMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 动态表单配置Repository
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysFormConfigService extends BaseSqlRepo<SysFormConfigMapper, SysFormConfig> {
}
