package com.bidr.kernel.mybatis.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.dao.mapper.SyncOutboxMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Title: SyncOutboxService
 * Description: 变更事件发件箱仓储（T3.3a）。
 * <p>
 * 只有两个动作：按 id 游标往后读（拉取口）、按事件时刻往前删（保留期口）。
 * 没有 update/disable 入口——发件箱语义是"已记账的事件不可改写"，任何就地修订都会让已经拉走过
 * 游标的消费方与库里的内容对不上。
 * </p>
 * <p>
 * 不加 {@code @DS}：事件表与业务表同库，跨库反而会让"记事件"和"改业务数据"落在两个事务域里。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class SyncOutboxService extends BaseSqlRepo<SyncOutboxMapper, SyncOutbox> {

    /**
     * 取 id 大于游标的事件，按 id 升序。
     * <p>
     * 游标为空或非正数＝从头拉（首次接入、或对端把游标弄丢了）；升序是硬要求，
     * 消费方把每条响应的最后一个 id 存成下次的 cursor，乱序会漏事件。
     * </p>
     *
     * @param cursor 上次拉到的最后一个 id，空/非正＝从头
     * @param limit  条数（int 拼接无注入面；归一规则属端点职责，见 SyncEventController）
     */
    public List<SyncOutbox> listAfter(Long cursor, int limit) {
        LambdaQueryWrapper<SyncOutbox> wrapper = super.getQueryWrapper()
                .gt(cursor != null && cursor > 0, SyncOutbox::getId, cursor)
                .orderByAsc(SyncOutbox::getId)
                .last("LIMIT " + limit);
        return super.select(wrapper);
    }

    /**
     * 物理删除事件时刻早于 {@code expired} 的行，返回删除行数。
     * <p>
     * 表上没有 valid 列（发件箱不留软删残骸，失效事件就是垃圾），所以这里是真删。
     * 返回行数是为了让保留期任务只在真删到东西时落日志——每夜的"删了 0 行"不该刷日志。
     * </p>
     * <p>
     * {@code expired} 为空＝没有边界，直接返回 0：宁可这轮不清理，也不能把无边界条件
     * 拼成"全表匹配"，那等于一次清空发件箱。
     * </p>
     */
    public int purgeBefore(Date expired) {
        if (expired == null) {
            return 0;
        }
        LambdaQueryWrapper<SyncOutbox> wrapper = super.getQueryWrapper()
                .lt(SyncOutbox::getOccurredAt, expired);
        return super.getBaseMapper().delete(wrapper);
    }
}