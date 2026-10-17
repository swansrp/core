package com.bidr.forge.service.dataset;

import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.vo.dataset.SysDatasetVO;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据集主表 Portal Service
 *
 * @author sharp
 * @since 2025-11-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDatasetPortalService extends BasePortalService<SysDataset, SysDatasetVO> {

    private final SysDatasetColumnService sysDatasetColumnService;
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;

    /**
     * 删除数据集后删除关联的字段配置和Portal配置
     */
    @Override
    public void afterDelete(IdReqVO vo) {
        // 查询要删除的数据集（此时valid已为0，需要直接查询）
        SysDataset dataset = getRepo().getById(vo.getId());

        if (dataset == null) {
            return;
        }

        // 1. 删除Dataset的字段配置
        try {
            sysDatasetColumnService.deleteByDatasetId(dataset.getId());
            log.info("删除Dataset[id={}]时，同步删除了其字段配置", dataset.getId());
        } catch (Exception e) {
            log.error("删除Dataset[id={}]的字段配置失败", dataset.getId(), e);
        }

        // 2. 删除关联的Portal配置
        try {
            List<Long> deletedPortalIds = sysPortalService.deleteByDataModeAndReferenceId(
                    PortalDataMode.DATASET.name(),
                    dataset.getId()
            );
            if (FuncUtil.isNotEmpty(deletedPortalIds)) {
                sysPortalColumnService.deleteByPortalIds(deletedPortalIds);
                log.info("删除Dataset[id={}]时，同步删除了 {} 个Portal配置", dataset.getId(), deletedPortalIds.size());
            }
        } catch (Exception e) {
            log.error("删除Dataset[id={}]关联的Portal配置失败", dataset.getId(), e);
        }

        super.afterDelete(vo);
    }
}
