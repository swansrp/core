package com.bidr.authorization.dao.repository.join;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.vo.account.AccountRes;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcUserGroupJoinService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 13:45
 */
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

    public List<AcUserGroup> getAcUserGroupByUserIdAndGroupType(Long userId, String groupType) {
        MPJLambdaWrapper<AcUserGroup> wrapper = new MPJLambdaWrapper<>(AcUserGroup.class).distinct()
                .leftJoin(AcGroup.class, AcGroup::getId, AcUserGroup::getGroupId)
                .leftJoin(AcUser.class, AcUser::getUserId, AcUserGroup::getUserId).eq(AcGroup::getType, groupType)
                .eq(AcUserGroup::getUserId, userId);
        return acUserGroupService.selectJoinList(AcUserGroup.class, wrapper);
    }
}
