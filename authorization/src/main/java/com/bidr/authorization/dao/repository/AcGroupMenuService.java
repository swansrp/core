package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcGroupMenu;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.mapper.AcGroupMenuMapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户组和菜单关联表Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class AcGroupMenuService extends BaseSqlRepo<AcGroupMenuMapper, AcGroupMenu> {

    private final RecursionService recursionService;
    private final AcUserGroupService acUserGroupService;

    public List<AcMenu> getAllMenu(Long userId, String clientType) {
        LambdaQueryWrapper<AcUserGroup> wr = new LambdaQueryWrapper<>();
        wr.eq(AcUserGroup::getUserId, userId);
        List<AcUserGroup> acUserGroups = acUserGroupService.select(wr);
        Set<Long> groupIds = new HashSet<>();
        for (AcUserGroup acUserGroup : acUserGroups) {
            if (DataPermitScopeDict.SUBORDINATE.getValue().equals(acUserGroup.getDataScope())) {
                List<Long> subGroup = recursionService.getChildList(AcGroup::getId, AcGroup::getPid, acUserGroup.getGroupId());
                if (FuncUtil.isNotEmpty(subGroup)) {
                    groupIds.addAll(subGroup);
                }
            }
            groupIds.add(acUserGroup.getGroupId());
        }
        if (FuncUtil.isNotEmpty(groupIds)) {
            MPJLambdaWrapper<AcGroupMenu> wrapper = super.getMPJLambdaWrapper();
            wrapper.distinct();
            wrapper.selectAll(AcMenu.class);
            wrapper.innerJoin(AcMenu.class, AcMenu::getMenuId, AcGroupMenu::getMenuId);
            wrapper.in(AcGroupMenu::getGroupId, groupIds);
            wrapper.eq(AcMenu::getClientType, clientType);
            wrapper.eq(AcMenu::getStatus, CommonConst.YES);
            wrapper.eq(AcMenu::getVisible, CommonConst.YES);
            wrapper.orderByAsc(AcMenu::getShowOrder);
            return super.selectJoinList(AcMenu.class, wrapper);
        } else {
            return new ArrayList<>();
        }
    }    // 仅包含业务逻辑方法，不包含DDL定义
}
