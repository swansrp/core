package com.bidr.forge.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.forge.dao.entity.SysDatasetSqlSnapshot;
import com.bidr.forge.dao.mapper.SysDatasetSqlSnapshotMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据集 SQL 快照 Repository Service
 *
 * @author sharp
 * @since 2026-10-30
 */
@Service
public class SysDatasetSqlSnapshotService extends BaseSqlRepo<SysDatasetSqlSnapshotMapper, SysDatasetSqlSnapshot> {

    /**
     * 查询数据集的 SQL 快照列表(新→旧, 最多 limit 条)
     */
    public List<SysDatasetSqlSnapshot> listByDatasetId(Long datasetId, int limit) {
        LambdaQueryWrapper<SysDatasetSqlSnapshot> wrapper = getQueryWrapper();
        wrapper.eq(SysDatasetSqlSnapshot::getDatasetId, datasetId);
        // 列表不带 sql_text 全文(几十KB/条), 查看单条走详情接口
        wrapper.select(SysDatasetSqlSnapshot.class, i -> !"sql_text".equals(i.getColumn()));
        wrapper.orderByDesc(SysDatasetSqlSnapshot::getId);
        wrapper.last("LIMIT " + Math.max(1, limit));
        return super.select(wrapper);
    }

    /**
     * 最新一条快照(无则 null)
     */
    public SysDatasetSqlSnapshot getLatest(Long datasetId) {
        LambdaQueryWrapper<SysDatasetSqlSnapshot> wrapper = getQueryWrapper();
        wrapper.eq(SysDatasetSqlSnapshot::getDatasetId, datasetId);
        wrapper.orderByDesc(SysDatasetSqlSnapshot::getId);
        wrapper.last("LIMIT 1");
        return super.selectOne(wrapper);
    }

    /**
     * 插入快照
     */
    public void insertSnapshot(SysDatasetSqlSnapshot snapshot) {
        super.insert(snapshot);
    }
}
