package com.bidr.admin.service.table;

import com.bidr.admin.dao.entity.SysPortalTable;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.PortalTableVO;
import com.bidr.kernel.utils.ConditionVariableUtil;
import org.springframework.stereotype.Service;

/**
 * 表格展示配置 Portal Service
 *
 * @author Sharp
 */
@Service
public class SysPortalTablePortalService extends BasePortalService<SysPortalTable, PortalTableVO> {

    @Override
    public void beforeAdd(SysPortalTable entity) {
        super.beforeAdd(entity);
        ConditionVariableUtil.validateTokens(entity.getFixedCondition());
    }

    @Override
    public void beforeUpdate(SysPortalTable entity) {
        super.beforeUpdate(entity);
        ConditionVariableUtil.validateTokens(entity.getFixedCondition());
    }
}
