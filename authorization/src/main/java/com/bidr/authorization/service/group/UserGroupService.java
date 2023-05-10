package com.bidr.authorization.service.group;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.repository.AcGroupService;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.vo.group.BindUserGroupReq;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AdminUserGroupBindService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 17:56
 */
@Service
@RequiredArgsConstructor
public class UserGroupService {

    private final AcUserGroupService acUserGroupService;

    private final AcGroupService acGroupService;


    public List<UserGroupTreeRes> getTree() {
        List<AcGroup> groups = acGroupService.list();
        return ReflectionUtil.buildTree(UserGroupTreeRes::setChildren, groups, AcGroup::getId, AcGroup::getPid);
    }

    public void addGroup(AcGroup req) {
        acGroupService.insert(req);
    }

    public void bind(BindUserGroupReq req) {

    }
}
