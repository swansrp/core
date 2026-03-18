package com.bidr.admin.service.table;

import com.bidr.admin.dao.entity.SysPortalTableFilter;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.PortalTableFilterVO;
import org.springframework.stereotype.Service;

/**
 * 表格报表筛选项 Portal Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTableFilterPortalService extends BasePortalService<SysPortalTableFilter, PortalTableFilterVO> {
    // 业务逻辑方法

    @Override
    public void beforeAdd(SysPortalTableFilter sysPortalTableFilter) {
        super.beforeAdd(sysPortalTableFilter);
    }
}
