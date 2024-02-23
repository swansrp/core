package com.bidr.admin.service;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.vo.*;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.KeyValueResVO;
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
        return getPortalWithColumnsConfig(req.getName());
    }

    public PortalWithColumnsRes getPortalWithColumnsConfig(String name) {
        SysPortal portal = sysPortalService.getByName(name);
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
        if (FuncUtil.isEmpty(req.getPidColumn())) {
            portal.setPidColumn(StringUtil.EMPTY);
        }
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
    public void deletePortalConfig(IdReqVO req) {
        sysPortalService.deleteById(req.getId());
        sysPortalColumnService.deleteByPortalId(req.getId());
    }

    public void validatePortalExisted(PortalReq req) {
        Validator.assertFalse(sysPortalService.existedByName(req.getName()), ErrCodeSys.SYS_ERR_MSG,
                "表格编码已存在,请删除原有表格或者使用其他编码");
    }

    @Transactional(rollbackFor = Exception.class)
    public void copyPortalConfig(PortalCopyReq req) {
        Validator.assertFalse(sysPortalService.existedByName(req.getTargetName()), ErrCodeSys.PA_DATA_HAS_EXIST,
                "表格编码");
        SysPortal sourcePortal = sysPortalService.getById(req.getSourceConfigId());
        Validator.assertNotNull(sourcePortal, ErrCodeSys.PA_DATA_NOT_EXIST, "实体");
        PortalWithColumnsRes sourcePortalWithColumn = Resp.convert(sourcePortal, PortalWithColumnsRes.class);
        SysPortal targetPortal = ReflectionUtil.copy(sourcePortal, SysPortal.class);
        targetPortal.setId(null);
        targetPortal.setName(req.getTargetName());
        targetPortal.setDisplayName(req.getTargetDisplayName());
        sysPortalService.insert(targetPortal);
        List<SysPortalColumn> columnList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(sourcePortalWithColumn.getColumns())) {
            for (SysPortalColumn column : sourcePortalWithColumn.getColumns()) {
                column.setId(null);
                column.setPortalId(targetPortal.getId());
                columnList.add(column);
            }
        }
        sysPortalColumnService.insert(columnList);
    }
}
