package com.bidr.admin.manage.permit.service;

import com.bidr.admin.manage.permit.vo.AcPermitApplyVO;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcPermitApply;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.utils.DbUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限申请表 Portal 服务
 *
 * @author sharp
 * @since 2026-02-06
 */
@Service
@RequiredArgsConstructor
public class AcPermitApplyPortalService extends BasePortalService<AcPermitApply, AcPermitApplyVO> {
    @Override
    public void getJoinWrapper(MPJLambdaWrapper<AcPermitApply> wrapper) {
        super.getJoinWrapper(wrapper);
        wrapper.leftJoin(AcUser.class, DbUtil.getTableName(AcUser.class), AcUser::getCustomerNumber, AcPermitApply::getCustomerNumber);
        wrapper.leftJoin(AcMenu.class, DbUtil.getTableName(AcMenu.class), AcMenu::getMenuId, AcPermitApply::getMenuId);
    }
}