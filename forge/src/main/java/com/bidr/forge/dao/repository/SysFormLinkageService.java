package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.SysFormLinkage;
import com.bidr.forge.dao.mapper.SysFormLinkageMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 表单项联动配置Repository
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
public class SysFormLinkageService extends BaseSqlRepo<SysFormLinkageMapper, SysFormLinkage> {
}
