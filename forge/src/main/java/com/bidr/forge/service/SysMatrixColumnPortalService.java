package com.bidr.forge.service;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.vo.SysMatrixColumnVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 矩阵字段配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
@RequiredArgsConstructor
public class SysMatrixColumnPortalService extends BasePortalService<SysMatrixColumn, SysMatrixColumnVO> {
}
