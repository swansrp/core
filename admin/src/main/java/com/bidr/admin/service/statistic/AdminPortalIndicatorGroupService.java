package com.bidr.admin.service.statistic;

import com.bidr.admin.dao.entity.SysPortalIndicatorGroup;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.admin.vo.statistic.PortalIndicatorGroupVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

/**
 * Title: AdminPortalIndicatorGroupService
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 13:37
 */
@Service
public class AdminPortalIndicatorGroupService extends BasePortalService<SysPortalIndicatorGroup, PortalIndicatorGroupVO> {


    @Override
    public void getJoinWrapper(MPJLambdaWrapper<SysPortalIndicatorGroup> wrapper) {
        super.getJoinWrapper(wrapper);
    }
}
