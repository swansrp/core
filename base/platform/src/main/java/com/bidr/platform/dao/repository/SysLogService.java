package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.dict.log.ProjectModule;
import com.bidr.platform.dao.entity.SysLog;
import com.bidr.platform.dao.mapper.SysLogDao;
import com.bidr.platform.vo.log.LogReq;
import com.bidr.platform.vo.log.LogRes;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * @author Sharp
 */
@Slf4j
@Service
public class SysLogService extends BaseSqlRepo<SysLogDao, SysLog> {

    /**
     * 单批行数：一条 {@code DELETE ... WHERE log_id IN (n)} 的行锁、undo 与 row-binlog 规模就以此为上限。
     * 表能长到几 GB 量级，所以批大小是运维参数而不是审美选择。
     */
    private static final int CLEAN_BATCH_ROWS = 1000;

    /** 单轮删除行数上限：攒下的留给下一夜接着删 */
    private static final long CLEAN_MAX_ROWS = 1_000_000L;

    /**
     * 单轮耗时上限。清表这类"越跑越久"的任务必须自己掐表：它和别的服务共用连接池与
     * InnoDB 缓冲池，不设闸等于让一次清理挤掉同库上的正常请求。
     */
    private static final long CLEAN_MAX_MILLIS = 5 * 60_000L;

    public List<LogRes> getLog(LogReq req) {
        MPJLambdaWrapper<SysLog> wrapper = new MPJLambdaWrapper<>();
        buildSelectWrapper(wrapper, SysLog.class, LogRes.class);
        wrapper.in(FuncUtil.isNotEmpty(req.getModuleId()), SysLog::getModuleId, req.getModuleId());
        wrapper.eq(SysLog::getEnvType, req.getEnvType());
        wrapper.in(FuncUtil.isNotEmpty(req.getLogLevel()), SysLog::getLogLevel, req.getLogLevel());
        wrapper.eq(FuncUtil.isNotEmpty(req.getRequestId()), SysLog::getRequestId, req.getRequestId());
        wrapper.eq(FuncUtil.isNotEmpty(req.getTraceId()), SysLog::getTraceId, req.getTraceId());
        wrapper.eq(FuncUtil.isNotEmpty(req.getRequestIP()), SysLog::getRequestIp, req.getRequestIP());
        wrapper.eq(FuncUtil.isNotEmpty(req.getUserIP()), SysLog::getUserIp, req.getUserIP());
        wrapper.eq(FuncUtil.isNotEmpty(req.getServerIP()), SysLog::getServerIp, req.getServerIP());
        wrapper.eq(FuncUtil.isNotEmpty(req.getThreadName()), SysLog::getThreadName, req.getThreadName());

        if (FuncUtil.isNotEmpty(req.getContent())) {
            final String[] andArray = req.getContent().split(" ");
            final String[] orArray = req.getContent().split("\\|");
            if (andArray.length > 1) {
                wrapper.nested(wr -> {
                    for (String s : andArray) {
                        wr.like(FuncUtil.isNotEmpty(s), SysLog::getContent, s);
                    }
                });
            } else if (orArray.length > 1) {
                wrapper.nested(wr -> {
                    for (String s : orArray) {
                        wr.like(FuncUtil.isNotEmpty(s), SysLog::getContent, s).or();
                    }
                });
            } else {
                wrapper.like(FuncUtil.isNotEmpty(req.getContent()), SysLog::getContent, req.getContent());
            }
        } else {
            wrapper.like(FuncUtil.isNotEmpty(req.getContent()), SysLog::getContent, req.getContent());
        }

        wrapper.orderByDesc(SysLog::getCreateTime, SysLog::getLogSeq);
        if (FuncUtil.isEmpty(req.getStartAt()) || FuncUtil.isEmpty(req.getEndAt())) {
            wrapper.last(" limit 500");
        } else {
            wrapper.gt(SysLog::getCreateTime, req.getStartAt());
            wrapper.le(SysLog::getCreateTime, req.getEndAt());
        }
        if (FuncUtil.isNotEmpty(req.getBlockMessage())) {
            for (String block : req.getBlockMessage()) {
                wrapper.notLike(SysLog::getContent, block);
            }
        }
        return selectJoinList(LogRes.class, wrapper);
    }

    public List<ProjectModule> getProjectModule() {
        MPJLambdaWrapper<SysLog> wrapper = new MPJLambdaWrapper<>();
        wrapper.selectAs(SysLog::getProjectId, ProjectModule::getProjectId);
        wrapper.selectAs(SysLog::getModuleId, ProjectModule::getModuleId);
        wrapper.groupBy(SysLog::getProjectId, SysLog::getModuleId);
        wrapper.orderByAsc(SysLog::getProjectId, SysLog::getModuleId);
        return selectJoinList(ProjectModule.class, wrapper);
    }

    /**
     * 清除 {@code expired} 之前写入的日志——语义与旧实现（{@code create_time <= expired} 一次删光）一致，
     * 但改成<b>沿主键升序分段批删</b>。补了 {@code create_time} 索引（SysLogSchema v1）也仍要这么写：
     * 索引只让"找到要删的行"变便宜，"一条语句删掉几百万行"这件事本身不会变——那一次事务的
     * 行锁、undo 与 row-binlog 仍按整段历史计，表越大越接近把库拖死（删完空间也不归还 OS，那是另一件事）。
     * <p>
     * 🔴 为什么按 log_id 走而不按 create_time 过滤：{@code log_id} 是 AUTO_INCREMENT，
     * 升序≈写入时序，所以"从表头往后读一段、撞到还没过期的行就停"就能覆盖全部过期数据，
     * 每轮实际只读只删"到期那部分"，新鲜区一行都不碰。异步 appender 的毫秒级乱序会让
     * 极少数行排在新鲜区之后，它们只是延到下一夜被删（下一夜的边界已越过它们），不影响保留期。
     * <p>
     * 🔴 单轮必须设预算闸：即便调度线程池已由框架抬到 4 条（TaskSchedulerPoolConfig），
     * 一次不设上限的清理仍会长时间占住连接与缓冲池；剩下的量由下一夜从真正的表头续上
     * （游标不跨轮持久化）。
     *
     * @param expired 保留边界，早于（含）该时刻的行被删；null 直接不删
     */
    public void cleanLog(Date expired) {
        CleanReport report = cleanLog(expired, System::currentTimeMillis);
        if (report.deletedRows > 0) {
            log.info("sys_log 清理：删除 {} 行（游标至 log_id={}，耗时 {}ms）；空间不随之归还 OS，要还盘另做 OPTIMIZE TABLE",
                    report.deletedRows, report.lastScannedId, report.elapsedMillis);
        }
        if (report.cutByBudget) {
            log.warn("sys_log 清理触发单轮预算后收口（已删 {} 行，游标停在 log_id={}），剩余量下一夜接着删",
                    report.deletedRows, report.lastScannedId);
        }
    }

    /**
     * 批删主体，时钟与两个数据口都可替换——{@code clock} 注入是为了让耗时预算这条腿能被单测确定性打到。
     */
    protected CleanReport cleanLog(Date expired, LongSupplier clock) {
        CleanReport report = new CleanReport();
        if (expired == null) {
            return report;
        }
        long startAt = clock.getAsLong();
        Long cursor = null;
        while (true) {
            List<SysLog> head = readLogHead(cursor);
            if (head == null || head.isEmpty()) {
                break;
            }
            cursor = head.get(head.size() - 1).getLogId();
            report.lastScannedId = cursor;
            List<SysLog> stale = new ArrayList<>(head.size());
            boolean reachedFresh = false;
            for (SysLog row : head) {
                Date createTime = row.getCreateTime();
                if (createTime == null) {
                    // 旧口径的 le() 也匹配不到 NULL：依旧不删，但不能让它永久挡住后面的行
                    continue;
                }
                if (createTime.after(expired)) {
                    reachedFresh = true;
                    break;
                }
                stale.add(row);
            }
            if (!stale.isEmpty()) {
                deleteByIds(stale);
                report.deletedRows += stale.size();
            }
            if (reachedFresh) {
                break;
            }
            if (report.deletedRows >= CLEAN_MAX_ROWS || clock.getAsLong() - startAt >= CLEAN_MAX_MILLIS) {
                report.cutByBudget = true;
                break;
            }
        }
        report.elapsedMillis = clock.getAsLong() - startAt;
        return report;
    }

    /**
     * 取游标之后的一批，只投影 {@code log_id + create_time}：判过期不需要正文，
     * 而 {@code content} 是 longtext，把它 SELECT 出来等于白读一遍溢出页。
     */
    protected List<SysLog> readLogHead(Long cursor) {
        LambdaQueryWrapper<SysLog> wrapper = super.getQueryWrapper();
        wrapper.select(SysLog::getLogId, SysLog::getCreateTime);
        wrapper.gt(cursor != null, SysLog::getLogId, cursor);
        wrapper.orderByAsc(SysLog::getLogId);
        wrapper.last("limit " + CLEAN_BATCH_ROWS);
        return super.list(wrapper);
    }

    /** 一批一条 {@code DELETE ... WHERE log_id IN (...)}：走主键，批大小即事务规模 */
    protected void deleteByIds(List<SysLog> rows) {
        List<Long> idList = new ArrayList<>(rows.size());
        for (SysLog row : rows) {
            idList.add(row.getLogId());
        }
        LambdaQueryWrapper<SysLog> wrapper = super.getQueryWrapper();
        wrapper.in(SysLog::getLogId, idList);
        super.delete(wrapper);
    }

    /** 一轮清理的结果，供日志与单测断言（包内可见，测试与同类走位共用） */
    static class CleanReport {
        long deletedRows;
        Long lastScannedId;
        long elapsedMillis;
        boolean cutByBudget;
    }
}
