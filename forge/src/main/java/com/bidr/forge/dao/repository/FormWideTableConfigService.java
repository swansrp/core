package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.FormWideTableConfig;
import com.bidr.forge.dao.mapper.FormWideTableConfigMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 宽表收集配置 Repository Service
 *
 * @author sharp
 */
@Service
public class FormWideTableConfigService extends BaseSqlRepo<FormWideTableConfigMapper, FormWideTableConfig> {
}
