package com.bidr.forge.service.dataset;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.vo.dataset.SysDatasetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据集主表 Portal Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
@RequiredArgsConstructor
public class SysDatasetPortalService extends BasePortalService<SysDataset, SysDatasetVO> {
}
