package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.dao.repository.join.AcUserGroupJoinService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.vo.group.*;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AdminUserGroupBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 14:05
 */
@Service
@RequiredArgsConstructor
public class AdminUserGroupBindService extends BaseBindRepo<AcGroup, AcUserGroup, AcUser, AcGroup, GroupAccountRes> {

    private final AcUserGroupService acUserGroupService;
    private final AcUserGroupJoinService acUserGroupJoinService;

    public void updateAcUserGroup(AcUserGroup acUserGroup) {
        acUserGroupService.updateById(acUserGroup);
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindList(BindGroupUserListDataScopeReq req) {
        if (FuncUtil.isNotEmpty(req.getAttachIdList())) {
            for (Object attachId : req.getAttachIdList()) {
                AcUserGroup acUserGroup = buildBindEntity(attachId, req.getEntityId());
                acUserGroup.setDataScope(req.getDataScope());
                acUserGroupService.insertOrUpdate(acUserGroup);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void bind(BindGroupUserDataScopeReq req) {
        AcUserGroup acUserGroup = buildBindEntity(req.getAttachId(), req.getEntityId());
        acUserGroup.setDataScope(req.getDataScope());
        acUserGroupService.insertOrUpdate(acUserGroup);
    }

    public List<GroupAccountRes> searchBindList(BindUserReq req) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.selectAll(getAttachClass()).select(bindEntityId()).leftJoin(getBindClass(), bindAttachId(), attachId())
                .eq(bindEntityId(), req.getGroupId())
                .like(FuncUtil.isNotEmpty(req.getName()), AcUser::getName, req.getName())
                .eq(FuncUtil.isNotEmpty(req.getDataScope()), AcUserGroup::getDataScope, req.getDataScope());
        return attachRepo().selectJoinList(GroupAccountRes.class, wrapper);
    }

    @Override
    protected SFunction<AcUserGroup, ?> bindEntityId() {
        return AcUserGroup::getGroupId;
    }

    @Override
    protected SFunction<AcUserGroup, ?> bindAttachId() {
        return AcUserGroup::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> attachId() {
        return AcUser::getUserId;
    }

    @Override
    protected SFunction<AcGroup, ?> entityId() {
        return AcGroup::getId;
    }

    public Collection<GroupAccountRes> getUserListByGroupType(String name) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.selectAll(getAttachClass()).select(bindEntityId()).leftJoin(getBindClass(), bindAttachId(), attachId())
                .leftJoin(getEntityClass(), entityId(), bindEntityId()).eq(AcGroup::getType, name);
        return distinct(attachRepo().selectJoinList(GroupAccountRes.class, wrapper));
    }

    private Collection<GroupUserRes> distinct(List<GroupAccountRes> selectJoinList) {
        Map<String, GroupUserRes> filter = new LinkedHashMap<>();
        if(FuncUtil.isNotEmpty(selectJoinList)) {
            for (GroupAccountRes account : selectJoinList) {
                filter.put(account.getCustomerNumber(), ReflectionUtil.copy(account, GroupUserRes.class));
            }
        }
        return filter.values();
    }

    public Collection<GroupUserRes> getDataScopeUserListByGroupType(String name) {
        List<String> customerNumberList = acUserGroupJoinService.getCustomerNumberListFromDataScope(
                AccountContext.getOperator(), name);
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.selectAll(getAttachClass()).select(bindEntityId()).leftJoin(getBindClass(), bindAttachId(), attachId())
                .leftJoin(getEntityClass(), entityId(), bindEntityId()).eq(AcGroup::getType, name)
                .in(AcUser::getCustomerNumber, customerNumberList);
        return distinct(attachRepo().selectJoinList(GroupAccountRes.class, wrapper));
    }
}
