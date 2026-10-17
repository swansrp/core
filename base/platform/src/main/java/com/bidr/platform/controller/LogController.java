package com.bidr.platform.controller;

import com.bidr.platform.config.anno.ApiTrace;
import com.bidr.platform.service.log.LogService;
import com.bidr.platform.vo.log.LogReq;
import com.bidr.platform.vo.log.LogRes;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: LogController
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/14 10:43
 */
@Slf4j
@Api(tags = "系统基础 - 日志查询")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = "/web/log/fetch")
public class LogController {
    private final LogService logService;

    @ApiTrace(response = false)
    @RequestMapping(value = "", method = RequestMethod.POST)
    public List<LogRes> log(@RequestBody LogReq req) {
        return logService.getLog(req);
    }
}
