package com.bidr.forge.service.dataset;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.vo.dataset.SysDatasetColumnVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 数据集列配置 Portal Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Service
@RequiredArgsConstructor
public class SysDatasetColumnPortalService extends BasePortalService<SysDatasetColumn, SysDatasetColumnVO> {
}
