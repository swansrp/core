package com.bidr.admin.dao.repository;

import com.bidr.admin.dao.entity.SysPortalPivotColumn;
import com.bidr.admin.dao.mapper.SysPortalPivotColumnMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 透视报表父表头列配置 Repository Service
 *
 * @author Sharp
 */
@Service
public class SysPortalPivotColumnService extends BaseSqlRepo<SysPortalPivotColumnMapper, SysPortalPivotColumn> {
    // 仅包含业务逻辑方法，不包含DDL 定义。
}
