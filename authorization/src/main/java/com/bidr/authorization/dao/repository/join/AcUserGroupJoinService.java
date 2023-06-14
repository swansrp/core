package com.bidr.authorization.dao.repository.join;

import com.bidr.authorization.annotation.data.scope.GroupDataScopeHolder;
import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: AcUserGroupJoinService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 13:45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcUserGroupJoinService {

    private final RecursionService recursionService;
    private final AcUserService acUserService;
    private final AcUserGroupService acUserGroupService;

    public List<AccountRes> getUserByGroupType(String groupType) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(AcUser.class).distinct()
                .rightJoin(AcUserGroup.class, AcUserGroup::getUserId, AcUser::getUserId)
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId).eq(AcGroup::getType, groupType);
        return acUserService.selectJoinList(AccountRes.class, wrapper);
    }

    public List<String> getCustomerNumberListFromDataScope(String customerNumber, String groupName) {
        List<String> res = GroupDataScopeHolder.get(groupName, customerNumber);
        if (res != null) {
            return res;
        } else {
            res = new ArrayList<>();
        }
        List<AcUserGroup> groups = getAcUserGroupByUserIdAndGroupType(customerNumber, groupName);
        Set<String> permissions = new HashSet<>();
        if (FuncUtil.isNotEmpty(groups)) {
            for (AcUserGroup group : groups) {
                DataPermitScopeDict scope = DataPermitScopeDict.of(group.getDataScope());
                if (FuncUtil.isEmpty(scope)) {
                    log.error("错误的数据权限类型: {}-{}:{} ", group.getGroupId(), group.getUserId(),
                            group.getDataScope());
                    continue;
                }
                switch (scope) {
                    case OWNER:
                        permissions.add(customerNumber);
                        break;
                    case DEPARTMENT: {
                        permissions.addAll(getCustomerNumberList(group.getGroupId()));
                        break;
                    }
                    case SUBORDINATE: {
                        permissions.addAll(getSubordinateCustomerNumberList(group.getGroupId()));
                        break;
                    }
                    default:
                        break;
                }
            }
        } else {
            permissions.add(customerNumber);
        }
        if (FuncUtil.isNotEmpty(permissions)) {
            res.addAll(permissions);
        }

        return res;
    }

    public List<AcUserGroup> getAcUserGroupByUserIdAndGroupType(String customerNumber, String groupType) {
        Validator.assertNotNull(customerNumber, ErrCodeSys.PA_PARAM_NULL, "查询用户组数据权限, 用户编码");
        MPJLambdaWrapper<AcUserGroup> wrapper = new MPJLambdaWrapper<>(AcUserGroup.class).distinct()
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId)
                .leftJoin(AcUser.class, AcUser::getUserId, AcUserGroup::getUserId).eq(AcGroup::getType, groupType)
                .eq(AcUser::getCustomerNumber, customerNumber);
        return acUserGroupService.selectJoinList(AcUserGroup.class, wrapper);
    }

    public List<String> getCustomerNumberList(Long groupId) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<AcUser>().select(AcUser::getCustomerNumber)
                .leftJoin(AcUserGroup.class, AcUserGroup::getUserId, AcUser::getUserId).distinct()
                .eq(AcUserGroup::getGroupId, groupId);
        return acUserService.selectJoinList(String.class, wrapper);
    }

    public List<String> getSubordinateCustomerNumberList(Long groupId) {
        List subGroup = recursionService.getChildList(AcGroup::getId, AcGroup::getPid, groupId);
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<AcUser>().select(AcUser::getCustomerNumber)
                .leftJoin(AcUserGroup.class, AcUserGroup::getUserId, AcUser::getUserId).distinct()
                .in(AcUserGroup::getGroupId, subGroup);
        return acUserService.selectJoinList(String.class, wrapper);
    }
}
