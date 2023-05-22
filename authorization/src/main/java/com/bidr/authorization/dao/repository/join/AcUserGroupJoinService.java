package com.bidr.authorization.dao.repository.join;

import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.vo.account.AccountRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final AcUserService acUserService;
    private final AcUserGroupService acUserGroupService;

    public List<AccountRes> getUserByGroupType(String groupType) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(AcUser.class).distinct()
                .rightJoin(AcUserGroup.class, AcUserGroup::getUserId, AcUser::getUserId)
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId).eq(AcGroup::getType, groupType);
        return acUserService.selectJoinList(AccountRes.class, wrapper);
    }

    public Set<Long> getUserIdByDataScope(Long userId, String groupName) {
        List<AcUserGroup> groups = getAcUserGroupByUserIdAndGroupType(userId, groupName);
        Set<Long> permissions = new HashSet<>();
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
                        permissions.add(group.getUserId());
                        break;
                    case DEPARTMENT: {
                        permissions.addAll(acUserGroupService.getUserIdList(group.getGroupId()));
                        break;
                    }
                    case SUBORDINATE: {
                        permissions.addAll(acUserGroupService.getSubordinateUserIdList(group.getGroupId()));
                        break;
                    }
                    default:
                        break;
                }
            }
        } else {
            permissions.add(userId);
        }
        return permissions;
    }

    public List<AcUserGroup> getAcUserGroupByUserIdAndGroupType(Long userId, String groupType) {
        Validator.assertNotNull(userId, ErrCodeSys.PA_PARAM_NULL, "查询用户组数据权限, 用户id");
        MPJLambdaWrapper<AcUserGroup> wrapper = new MPJLambdaWrapper<>(AcUserGroup.class).distinct()
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId)
                .leftJoin(AcUser.class, AcUser::getUserId, AcUserGroup::getUserId).eq(AcGroup::getType, groupType)
                .eq(AcUserGroup::getUserId, userId);
        return acUserGroupService.selectJoinList(AcUserGroup.class, wrapper);
    }
}
