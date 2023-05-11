package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.mapper.AcUserGroupDao;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcUserGroupService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 16:33
 */
@Service
@RequiredArgsConstructor
public class AcUserGroupService extends BaseSqlRepo<AcUserGroupDao, AcUserGroup> {

    private final RecursionService recursionService;

    public List<Long> getUserIdList(Long groupId) {
        MPJLambdaWrapper<AcUserGroup> wrapper = new MPJLambdaWrapper<AcUserGroup>()
                .select(AcUserGroup::getUserId).distinct().eq(AcUserGroup::getGroupId, groupId);
        return super.selectJoinList(Long.class, wrapper);
    }

    public List<Long> getSubordinateUserIdList(Long groupId) {
        List subGroup = recursionService.getChildList(AcGroup::getId, AcGroup::getPid, groupId);
        MPJLambdaWrapper<AcUserGroup> wrapper = new MPJLambdaWrapper<AcUserGroup>()
                .select(AcUserGroup::getUserId).distinct()
                .in(AcUserGroup::getGroupId, subGroup);
        return super.selectJoinList(Long.class, wrapper);
    }
}
