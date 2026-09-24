package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.entity.SysPortalIndicator;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.statistic.PortalIndicatorVO;
import com.bidr.kernel.utils.ConditionVariableUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

/**
 * Title: AdminPortalIndicatorService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Service
public class AdminPortalIndicatorService extends BasePortalService<SysPortalIndicator, PortalIndicatorVO> {

    @Override
    public void getJoinWrapper(MPJLambdaWrapper<SysPortalIndicator> wrapper) {
        super.getJoinWrapper(wrapper);
    }

    @Override
    public void beforeAdd(SysPortalIndicator entity) {
        super.beforeAdd(entity);
        ConditionVariableUtil.validateTokens(entity.getCondition());
    }

    @Override
    public void beforeUpdate(SysPortalIndicator entity) {
        super.beforeUpdate(entity);
        ConditionVariableUtil.validateTokens(entity.getCondition());
    }
}
