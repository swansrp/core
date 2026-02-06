package com.bidr.admin.manage.permit.service;

import com.bidr.admin.manage.permit.vo.AcPermitApplyVO;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.dao.entity.AcPermitApply;
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

}