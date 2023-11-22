package com.bidr.platform.service.portal;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.dao.entity.SysPortal;
import com.bidr.platform.dao.repository.SysPortalColumnService;
import com.bidr.platform.dao.repository.SysPortalService;
import com.bidr.platform.vo.portal.PortalReq;
import com.bidr.platform.vo.portal.PortalRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Title: PortalService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:02
 */
@Service
@RequiredArgsConstructor
public class PortalService {
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;

    public PortalRes getPortalConfig(PortalReq req) {
        SysPortal portal = sysPortalService.getByName(req.getName());
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        return Resp.convert(portal, PortalRes.class);
    }

}
