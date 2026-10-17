package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalTable;
import com.bidr.admin.dao.mapper.SysPortalTableMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 表格展示配置 Repository Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTableService extends BaseSqlRepo<SysPortalTableMapper, SysPortalTable> {
    // 仅包含业务逻辑方法，不包含DDL 定义。
}
