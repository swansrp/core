package com.bidr.platform.controller;

import com.bidr.platform.utils.DateWeekUtil;
import com.bidr.platform.vo.week.VersionConvertReq;
import com.bidr.platform.vo.week.VersionConvertRes;
import com.bidr.platform.vo.week.VersionDateConvertReq;
import com.bidr.platform.vo.week.VersionDateConvertRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: SystemWeekConvertController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/21 10:23
 */
@Api(tags = "系统基础 - 年份周数转换")
@RequiredArgsConstructor
@RestController("SystemWeekConvertController")
@RequestMapping(value = "/web/week")
public class SystemWeekConvertController {

    @ApiOperation(value = "日历周信息转换")
    @RequestMapping(value = "/convert", method = RequestMethod.GET)
    public VersionConvertRes convertVersion(VersionConvertReq req) {
        return DateWeekUtil.convertVersion(req.getVersion());
    }

    @ApiOperation(value = "日历周信息转换")
    @RequestMapping(value = "/date/convert", method = RequestMethod.GET)
    public VersionDateConvertRes convertVersion(VersionDateConvertReq req) {
        return DateWeekUtil.convertVersion(req.getDate());
    }

}
