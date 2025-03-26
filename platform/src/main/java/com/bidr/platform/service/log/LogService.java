package com.bidr.platform.service.log;

import com.bidr.platform.dao.repository.SysLogService;
import com.bidr.platform.vo.log.LogReq;
import com.bidr.platform.vo.log.LogRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: LogService
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/14 10:44
 */
@Service
@RequiredArgsConstructor
public class LogService {
    private final SysLogService sysLogService;

    public List<LogRes> getLog(LogReq req) {
        return sysLogService.getLog(req);
    }
}
