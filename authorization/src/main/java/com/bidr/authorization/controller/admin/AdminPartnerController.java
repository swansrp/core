package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.service.admin.AdminPartnerService;
import com.bidr.authorization.vo.partner.PartnerReq;
import com.bidr.authorization.vo.partner.PartnerRes;
import com.bidr.authorization.vo.partner.QueryPartnerReq;
import com.bidr.authorization.vo.partner.QueryPartnerRes;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.vo.common.IdReqVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: AdminPartnerController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 17:21
 */
@Api(tags = "系统管理 - 对接平台管理")
@RestController("AdminPartnerController")
@RequestMapping(value = "/web/partner/admin")
public class AdminPartnerController extends BaseAdminController<AcPartner, QueryPartnerRes> {

    @Resource
    private AdminPartnerService adminPartnerService;

    @ApiOperation(value = "添加对接平台")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public PartnerRes addPartner(@RequestBody PartnerReq req) {
        return adminPartnerService.add(req);
    }

    @Override
    public void beforeUpdate(AcPartner entity) {
        entity.setAppKey(null);
        entity.setAppSecret(null);
        entity.setPlatform(null);
    }

    @ApiOperation(value = "获取接入列表")
    @RequestMapping(value = "/query", method = RequestMethod.POST)
    public Page<QueryPartnerRes> queryPartner(@RequestBody QueryPartnerReq req) {
        return adminPartnerService.query(req);
    }


    @RequestMapping(value = "/enable", method = RequestMethod.POST)
    public Boolean enable(@RequestBody IdReqVO vo) {
        return update(vo, AcPartner::getStatus, ActiveStatusDict.ACTIVATE.getValue());
    }

    @RequestMapping(value = "/disable", method = RequestMethod.POST)
    public Boolean disable(@RequestBody IdReqVO vo) {
        return update(vo, AcPartner::getStatus, ActiveStatusDict.DEACTIVATE.getValue());
    }

}
