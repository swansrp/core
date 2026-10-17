package com.bidr.admin.manage.partner.service;

import com.bidr.admin.manage.partner.vo.AcPartnerHistoryVO;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

/**
 * Title: AcPartnerHistoryPortalService
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 15:35
 */
@Service
public class AcPartnerHistoryPortalService extends BasePortalService<AcPartnerHistory, AcPartnerHistoryVO> {

    @Override
    public void getJoinWrapper(MPJLambdaWrapper<AcPartnerHistory> wrapper) {
        super.getJoinWrapper(wrapper);
        wrapper.orderByDesc(AcPartnerHistory::getResponseAt);
    }
}
