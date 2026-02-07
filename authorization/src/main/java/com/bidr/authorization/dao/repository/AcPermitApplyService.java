package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcPermitApply;
import com.bidr.authorization.dao.mapper.AcPermitApplyDao;
import com.bidr.kernel.constant.dict.common.ApprovalDict;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 权限申请表Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class AcPermitApplyService extends BaseSqlRepo<AcPermitApplyDao, AcPermitApply> {

    private final AcMenuService acMenuService;
    private final AcUserMenuService acUserMenuService;
    private final RecursionService recursionService;

    /**
     * 审批申请
     *
     * @param id      申请ID
     * @param pass    是否通过
     * @param remark  审批备注
     * @param auditor 审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String remark, String auditor) {
        AcPermitApply apply = super.getById(id);
        if (apply == null) {
            return;
        }

        if (pass) {
            apply.setStatus(ApprovalDict.APPROVAL.getValue());
            AcMenu acMenu = acMenuService.selectById(apply.getMenuId());
            if (acMenu != null) {
                // 审批通过，自动绑定权限
                acUserMenuService.replace(apply.getCustomerNumber(), apply.getMenuId());
                if (acMenu.getGrandId() != null) {
                    acUserMenuService.replace(apply.getCustomerNumber(), acMenu.getGrandId());
                }
                List<Long> parentList = recursionService.getParentList(AcMenu::getMenuId, AcMenu::getPid, apply.getMenuId());
                if (FuncUtil.isNotEmpty(parentList)) {
                    for (Long parentId : parentList) {
                        acUserMenuService.replace(apply.getCustomerNumber(), parentId);
                    }
                }
            }
        } else {
            apply.setStatus(ApprovalDict.REJECT.getValue());
        }

        apply.setAuditRemark(remark);
        apply.setAuditBy(auditor);
        apply.setAuditAt(new Date());
        super.updateById(apply);
    }

    public void applyUserPermit(String operator, Long menuId, String reason) {
        LambdaQueryWrapper<AcPermitApply> wrapper = super.getQueryWrapper();
        wrapper.eq(AcPermitApply::getCustomerNumber, operator)
                .eq(AcPermitApply::getMenuId, menuId);
        AcPermitApply apply = super.selectOne(wrapper);
        if (apply == null) {
            apply = new AcPermitApply();
        }
        apply.setCustomerNumber(operator);
        apply.setMenuId(menuId);
        apply.setReason(reason);
        apply.setStatus(ApprovalDict.APPLY.getValue());
        if (FuncUtil.isEmpty(apply.getId())) {
            super.insert(apply);
        } else {
            super.updateById(apply);
        }
    }

    public AcPermitApply getUserPermitApply(String operator, Long menuId) {
        LambdaQueryWrapper<AcPermitApply> wrapper = super.getQueryWrapper();
        wrapper.eq(AcPermitApply::getCustomerNumber, operator)
                .eq(AcPermitApply::getMenuId, menuId);
        return super.selectOne(wrapper);
    }
}
