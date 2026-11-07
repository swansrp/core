package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.vo.partner.PartnerReq;
import com.bidr.authorization.vo.partner.PartnerRes;
import com.bidr.authorization.vo.partner.QueryPartnerReq;
import com.bidr.authorization.vo.partner.QueryPartnerRes;
import com.bidr.authorization.vo.partner.RegisterSubsystemReq;
import com.bidr.authorization.vo.partner.RegisterSubsystemRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.RandomUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Title: AdminPartnerService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 17:34
 */
@Service
@RequiredArgsConstructor
public class AdminPartnerService {

    private final AcPartnerService acPartnerService;
    private final AcSubsystemService acSubsystemService;


    public Page<QueryPartnerRes> query(QueryPartnerReq req) {
        Page<AcPartner> partner = acPartnerService.queryPartner(req);
        return Resp.convert(partner, QueryPartnerRes.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerRes add(PartnerReq req) {
        AcPartner partner = ReflectionUtil.copy(req, AcPartner.class);
        String appKey = RandomUtil.getString(8);
        String appSecret = RandomUtil.getString(16);
        partner.setAppKey(appKey);
        partner.setAppSecret(appSecret);
        partner.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        acPartnerService.insert(partner);
        return new PartnerRes(appKey, appSecret);

    }

    /**
     * 渠道注册子系统（方案B 棒2）：一个事务先建 partner（appKey/appSecret 服务端生成，
     * 复用 {@link #add} 口径）再插 ac_subsystem（partner_id=新 partner id，
     * uk_partner_id 保证一对一），密钥一次性展示语义不变＋appCode 回显。
     * <p>🔴 子系统参数先验（写库前 fail-fast）：非法 ⇒ 两表零写入（@Transactional
     * 兜底插腿 DB 异常时的整体回滚，离线腿钉「partner 也不落」的先验路径）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public RegisterSubsystemRes registerSubsystem(RegisterSubsystemReq req) {
        Validator.assertTrue(FuncUtil.isNotEmpty(req.getAppCode()), ErrCodeSys.SYS_ERR_MSG, "appCode不能为空");
        Validator.assertTrue(FuncUtil.isNotEmpty(req.getName()), ErrCodeSys.SYS_ERR_MSG, "子系统显示名不能为空");

        AcPartner partner = new AcPartner();
        String appKey = RandomUtil.getString(8);
        String appSecret = RandomUtil.getString(16);
        partner.setAppKey(appKey);
        partner.setAppSecret(appSecret);
        partner.setRemark(req.getRemark());
        partner.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        acPartnerService.insert(partner);

        AcSubsystem subsystem = ReflectionUtil.copy(req, AcSubsystem.class);
        subsystem.setPartnerId(partner.getId());
        // status 置启用（I-2）：门户可见面按 status='1' 过滤（InkHub SubsystemController 口径），
        // 不置则一键注册出的子系统在门户 /visible 永不出现——注册即门户可见，
        // 与 AcSubsystemSeeder 兜底行口径一致；下线走管理面停用即可。
        subsystem.setStatus(CommonConst.YES);
        acSubsystemService.insert(subsystem);

        return new RegisterSubsystemRes(appKey, appSecret, subsystem.getAppCode());
    }
}

