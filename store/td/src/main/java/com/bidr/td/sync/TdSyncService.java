package com.bidr.td.sync;

import com.bidr.td.repository.BaseTdRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

/**
 * 异步同步服务：接收实体变更事件，执行 TD 操作。
 */
public class TdSyncService<T> {

    private static final Logger log = LoggerFactory.getLogger(TdSyncService.class);

    private final BaseTdRepo<T> tdRepo;

    public TdSyncService(BaseTdRepo<T> tdRepo) {
        this.tdRepo = tdRepo;
    }

    /**
     * 异步写入单个数据
     */
    @Async
    public void syncInsert(String subTableName, T entity) {
        try {
            tdRepo.insertOne(subTableName, entity);
            log.debug("TD sync insert success: {} -> {}", subTableName, entity);
        } catch (Exception e) {
            log.error("TD sync insert failed for subTable={}", subTableName, e);
            // TODO: 将失败记录写入 td_sync_log 表进行重试
        }
    }

    /**
     * 异步批量写入
     */
    @Async
    public void syncInsertBatch(String subTableName, java.util.List<T> entities) {
        try {
            tdRepo.insertBatch(subTableName, entities);
            log.debug("TD sync insert batch success: {} -> {} records", subTableName, entities.size());
        } catch (Exception e) {
            log.error("TD sync insert batch failed for subTable={}, count={}", subTableName, entities.size(), e);
            // TODO: 将失败记录写入 td_sync_log 表进行重试
        }
    }
}
