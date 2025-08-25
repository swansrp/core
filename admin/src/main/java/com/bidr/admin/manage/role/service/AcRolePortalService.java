package com.bidr.admin.manage.role.service;

import com.bidr.admin.manage.role.vo.AcRoleVO;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.kernel.constant.CommonConst;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

/**
 * Title: AcRolePortalService
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/8/25 14:16
 */
@Service
public class AcRolePortalService extends BasePortalService<AcRole, AcRoleVO> {

    @Override
    public void getJoinWrapper(MPJLambdaWrapper<AcRole> wrapper) {
        super.getJoinWrapper(wrapper);
        wrapper.eq(AcRole::getStatus, CommonConst.YES);
        wrapper.orderByAsc(AcRole::getDisplayOrder);
    }
}
