package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.mapper.AcUserGroupDao;
import com.bidr.kernel.mybatis.dao.repository.RecursionService;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public boolean existedByGroupId(Long groupId) {
        List subGroup = recursionService.getChildList(AcGroup::getId, AcGroup::getPid, groupId);
        LambdaQueryWrapper<AcUserGroup> wrapper = super.getQueryWrapper()
                .in(FuncUtil.isNotEmpty(subGroup), AcUserGroup::getGroupId, subGroup);
        return super.existed(wrapper);
    }

    public void deleteByGroupId(Object groupId) {
        LambdaQueryWrapper<AcUserGroup> wrapper = super.getQueryWrapper()
                .eq(AcUserGroup::getGroupId, groupId);
        super.delete(wrapper);
    }

    public long countByGroupIds(List<Long> groupIds) {
        if (FuncUtil.isEmpty(groupIds)) {
            return 0;
        }
        LambdaQueryWrapper<AcUserGroup> wrapper = super.getQueryWrapper()
                .in(AcUserGroup::getGroupId, groupIds);
        return super.count(wrapper);
    }

    public List<AcUserGroup> selectByGroupIds(List<Long> groupIds) {
        if (FuncUtil.isEmpty(groupIds)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<AcUserGroup> wrapper = super.getQueryWrapper()
                .in(AcUserGroup::getGroupId, groupIds);
        return super.select(wrapper);
    }

    public void deleteByGroupIds(List<Long> groupIds) {
        if (FuncUtil.isEmpty(groupIds)) {
            return;
        }
        LambdaQueryWrapper<AcUserGroup> wrapper = super.getQueryWrapper()
                .in(AcUserGroup::getGroupId, groupIds);
        super.delete(wrapper);
    }
}
