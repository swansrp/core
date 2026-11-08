package com.bidr.authorization.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.service.admin.AdminPartnerService;
import com.bidr.authorization.vo.partner.PartnerReq;
import com.bidr.authorization.vo.partner.PartnerRes;
import com.bidr.authorization.vo.partner.QueryPartnerReq;
import com.bidr.authorization.vo.partner.QueryPartnerRes;
import com.bidr.authorization.vo.partner.RegisterSubsystemReq;
import com.bidr.authorization.vo.partner.RegisterSubsystemRes;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.validate.Validator;
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
@Auth(AuthAdmin.class)
public class AdminPartnerController extends BaseAdminController<AcPartner, QueryPartnerRes> {

    @Resource
    private AdminPartnerService adminPartnerService;

    @Resource
    private AcSubsystemService acSubsystemService;

    @ApiOperation(value = "添加对接平台")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public PartnerRes addPartner(@RequestBody PartnerReq req) {
        return adminPartnerService.add(req);
    }

    @ApiOperation(value = "渠道注册子系统（一键建渠道+应用，同一事务）")
    @RequestMapping(value = "/register-subsystem", method = RequestMethod.POST)
    public RegisterSubsystemRes registerSubsystem(@RequestBody RegisterSubsystemReq req) {
        return adminPartnerService.registerSubsystem(req);
    }

    @Override
    public void beforeUpdate(AcPartner entity) {
        entity.setAppKey(null);
        entity.setAppSecret(null);
        entity.setPlatform(null);
    }

    /**
     * 删除守卫（beforeUpdate 同款 hook 形态）：ac_subsystem.partner_id 有引用则拒删渠道
     * （M-6 弱引用无 FK，完整性在删除口兜）——先人工解除子系统绑定再删渠道。
     */
    @Override
    public void beforeDelete(IdReqVO vo) {
        Long partnerId = parsePartnerId(vo);
        if (partnerId != null) {
            AcSubsystem bound = acSubsystemService.getByPartnerId(partnerId);
            Validator.assertNull(bound, AccountErrCode.AC_PARTNER_HAS_SUBSYSTEM);
        }
    }

    /** id 不可解析为 partner id（Long）时守卫放行——后续基类按 id 删行自然报「删除失败」，不在此伪装业务异常。 */
    private Long parsePartnerId(IdReqVO vo) {
        if (vo == null || vo.getId() == null || vo.getId().trim().isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(vo.getId().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
