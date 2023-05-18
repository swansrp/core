package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.vo.group.BindUserReq;
import com.bidr.authorization.vo.group.GroupAccountRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public void updateAcUserGroup(AcUserGroup acUserGroup) {
        acUserGroupService.updateById(acUserGroup);
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
}
