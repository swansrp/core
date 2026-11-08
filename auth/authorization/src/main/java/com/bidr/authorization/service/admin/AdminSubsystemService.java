package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.vo.subsystem.PartnerOptionRes;
import com.bidr.authorization.vo.subsystem.QuerySubsystemReq;
import com.bidr.authorization.vo.subsystem.QuerySubsystemRes;
import com.bidr.authorization.vo.subsystem.SubsystemReq;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Title: AdminSubsystemService
 * Description: 应用管理 admin 面服务（方案B 棒2，照 AdminPartnerService 形态：controller 编排、
 * 本类组装、仓储层守卫）。
 *
 * <p>partnerAppKey＝两步查（非 SQL join）：分页取子系统行后按 partner_id 批量取 partner 投影——
 * uk_partner_id 语义下 partner 至多被一行引用，两步无放大（M-6 弱引用纪律：不建实体关联）。</p>
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Service
@RequiredArgsConstructor
public class AdminSubsystemService {

    private final AcSubsystemService acSubsystemService;
    private final AcPartnerService acPartnerService;

    /**
     * 分页列表：行带经 partner_id 查出的 partner_app_key。
     */
    public Page<QuerySubsystemRes> query(QuerySubsystemReq req) {
        Page<AcSubsystem> page = acSubsystemService.querySubsystem(req);
        Page<QuerySubsystemRes> res = Resp.convert(page, QuerySubsystemRes.class);
        fillPartnerAppKey(res.getRecords());
        return res;
    }

    /**
     * 新增：partner 绑定校验在仓储 insert 腿（AcSubsystemService.insert）。
     */
    public void add(SubsystemReq req) {
        AcSubsystem entity = ReflectionUtil.copy(req, AcSubsystem.class);
        acSubsystemService.insert(entity);
    }

    /**
     * 删除：菜单引用守卫在仓储 {@link AcSubsystemService#deleteWithGuard}。
     */
    public void delete(String appCode) {
        acSubsystemService.deleteWithGuard(appCode);
    }

    /**
     * 可绑定渠道下拉项：ac_partner 全表四字段投影（id/appKey/remark/status）。
     * <p>🔴 绝不含 appSecret——wrapper.select 收死投影列，密钥不离开 DB
     * （密钥只在注册/新建口一次性展示）。</p>
     */
    public List<PartnerOptionRes> partnerOptions() {
        LambdaQueryWrapper<AcPartner> wrapper = acPartnerService.getQueryWrapper();
        wrapper.select(AcPartner::getId, AcPartner::getAppKey, AcPartner::getRemark, AcPartner::getStatus);
        wrapper.orderByAsc(AcPartner::getId);
        List<PartnerOptionRes> options = new ArrayList<>();
        for (AcPartner partner : acPartnerService.select(wrapper)) {
            PartnerOptionRes option = new PartnerOptionRes();
            option.setId(partner.getId());
            option.setAppKey(partner.getAppKey());
            option.setRemark(partner.getRemark());
            option.setStatus(partner.getStatus());
            options.add(option);
        }
        return options;
    }

    /** 列表行回填 partnerAppKey：批量取 partner 的 (id, appKey) 投影，未绑定行留 null。 */
    private void fillPartnerAppKey(List<QuerySubsystemRes> rows) {
        if (FuncUtil.isEmpty(rows)) {
            return;
        }
        Set<Long> partnerIds = rows.stream()
                .map(QuerySubsystemRes::getPartnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (partnerIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<AcPartner> wrapper = acPartnerService.getQueryWrapper();
        wrapper.select(AcPartner::getId, AcPartner::getAppKey);
        wrapper.in(AcPartner::getId, partnerIds);
        Map<Long, String> appKeyById = acPartnerService.select(wrapper).stream()
                .collect(Collectors.toMap(AcPartner::getId, AcPartner::getAppKey));
        for (QuerySubsystemRes row : rows) {
            row.setPartnerAppKey(row.getPartnerId() == null ? null : appKeyById.get(row.getPartnerId()));
        }
    }
}
