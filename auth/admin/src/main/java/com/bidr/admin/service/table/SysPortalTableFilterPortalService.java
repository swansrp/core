package com.bidr.admin.service.table;

import com.bidr.admin.dao.entity.SysPortalTableFilter;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.PortalTableFilterVO;
import com.bidr.kernel.utils.ConditionVariableUtil;
import org.springframework.stereotype.Service;

/**
 * 表格报表筛选项 Portal Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTableFilterPortalService extends BasePortalService<SysPortalTableFilter, PortalTableFilterVO> {

    @Override
    public void beforeAdd(SysPortalTableFilter entity) {
        super.beforeAdd(entity);
        ConditionVariableUtil.validateTokens(entity.getDefaultValue());
    }

    @Override
    public void beforeUpdate(SysPortalTableFilter entity) {
        super.beforeUpdate(entity);
        ConditionVariableUtil.validateTokens(entity.getDefaultValue());
    }
}
