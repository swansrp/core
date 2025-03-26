package com.bidr.platform.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.dict.log.ProjectModule;
import com.bidr.platform.dao.entity.SysLog;
import com.bidr.platform.dao.mapper.SysLogDao;
import com.bidr.platform.vo.log.LogReq;
import com.bidr.platform.vo.log.LogRes;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Sharp
 */
@Service
public class SysLogService extends BaseSqlRepo<SysLogDao, SysLog> {
    public List<LogRes> getLog(LogReq req) {
        MPJLambdaWrapper<SysLog> wrapper = new MPJLambdaWrapper<>();
        buildSelectWrapper(wrapper, SysLog.class, LogRes.class);
        wrapper.eq(SysLog::getModuleId, req.getModuleId());
        wrapper.eq(SysLog::getEnvType, req.getEnvType());
        wrapper.in(FuncUtil.isNotEmpty(req.getLogLevel()), SysLog::getLogLevel, req.getLogLevel());
        wrapper.eq(FuncUtil.isNotEmpty(req.getRequestId()), SysLog::getRequestId, req.getRequestId());
        wrapper.eq(FuncUtil.isNotEmpty(req.getTraceId()), SysLog::getTraceId, req.getTraceId());
        wrapper.eq(FuncUtil.isNotEmpty(req.getRequestIP()), SysLog::getRequestIp, req.getRequestIP());
        wrapper.eq(FuncUtil.isNotEmpty(req.getUserIP()), SysLog::getUserIp, req.getUserIP());
        wrapper.eq(FuncUtil.isNotEmpty(req.getServerIP()), SysLog::getServerIp, req.getServerIP());
        wrapper.eq(FuncUtil.isNotEmpty(req.getThreadName()), SysLog::getThreadName, req.getThreadName());
        wrapper.like(FuncUtil.isNotEmpty(req.getContent()), SysLog::getContent, req.getContent());
        wrapper.orderByDesc(SysLog::getCreateTime, SysLog::getLogSeq);
        if (FuncUtil.isEmpty(req.getStartAt()) || FuncUtil.isEmpty(req.getEndAt())) {
            wrapper.last(" limit 500");
        } else {
            wrapper.gt(SysLog::getCreateTime, req.getStartAt());
            wrapper.le(SysLog::getCreateTime, req.getEndAt());
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
}
