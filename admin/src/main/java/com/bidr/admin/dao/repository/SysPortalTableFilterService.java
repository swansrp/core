package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalTableFilter;
import com.bidr.admin.dao.mapper.SysPortalTableFilterMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 表格报表筛选项 Repository Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTableFilterService extends BaseSqlRepo<SysPortalTableFilterMapper, SysPortalTableFilter> {
    // 仅包含业务逻辑方法，不包含DDL 定义。
}
