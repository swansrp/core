package com.bidr.platform.service.portal;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysPortal;
import com.bidr.platform.dao.entity.SysPortalColumn;
import com.bidr.platform.dao.repository.SysPortalColumnService;
import com.bidr.platform.dao.repository.SysPortalService;
import com.bidr.platform.vo.portal.PortalColumnReq;
import com.bidr.platform.vo.portal.PortalReq;
import com.bidr.platform.vo.portal.PortalUpdateReq;
import com.bidr.platform.vo.portal.PortalWithColumnsRes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    public PortalWithColumnsRes getPortalWithColumnsConfig(PortalReq req) {
        SysPortal portal = sysPortalService.getByName(req.getName());
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        return Resp.convert(portal, PortalWithColumnsRes.class);
    }

    public PortalUpdateReq getPortalConfig(PortalReq req) {
        SysPortal portal = sysPortalService.getByName(req.getName());
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        return Resp.convert(portal, PortalUpdateReq.class);
    }

    public List<KeyValueResVO> getPortalList(PortalReq req) {
        return sysPortalService.getPortalList(req.getName());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePortalConfig(PortalUpdateReq req) {
        SysPortal portal = ReflectionUtil.copy(req, SysPortal.class);
        sysPortalService.updateById(portal);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePortalColumnOrder(List<IdOrderReqVO> orderList) {
        if (FuncUtil.isNotEmpty(orderList)) {
            List<SysPortalColumn> entityList = new ArrayList<>();
            for (IdOrderReqVO order : orderList) {
                SysPortalColumn column = new SysPortalColumn();
                column.setId(Long.parseLong(StringUtil.parse(order.getId())));
                column.setDisplayOrder(order.getShowOrder());
                entityList.add(column);
            }
            sysPortalColumnService.updateBatchById(entityList);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePortalColumn(PortalColumnReq req) {
        SysPortalColumn column = ReflectionUtil.copy(req, SysPortalColumn.class);
        sysPortalColumnService.updateById(column);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePortalConfig(PortalReq req) {
        sysPortalService.deleteByName(req.getName());
    }
}
