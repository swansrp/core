package com.bidr.forge.service.dataset;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.vo.dataset.SysDatasetTableVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据集关联表 Portal Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
@RequiredArgsConstructor
public class SysDatasetTablePortalService extends BasePortalService<SysDatasetTable, SysDatasetTableVO> {
}
