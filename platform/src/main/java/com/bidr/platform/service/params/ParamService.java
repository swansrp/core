package com.bidr.platform.service.params;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.dao.repository.SysConfigService;
import com.bidr.platform.vo.params.QuerySysConfigReq;
import com.bidr.platform.vo.params.SysConfigRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Title: ParamService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/07 16:01
 */
@Service
@RequiredArgsConstructor
public class ParamService {
    private final SysConfigService sysConfigService;

    public Page<SysConfigRes> query(QuerySysConfigReq req) {
        Page<SysConfig> res = sysConfigService.querySysConfig(req);
        return Resp.convert(res, SysConfigRes.class);
    }
}
