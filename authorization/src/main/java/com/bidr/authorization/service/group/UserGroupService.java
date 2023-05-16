package com.bidr.authorization.service.group;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.authorization.dao.repository.AcGroupService;
import com.bidr.authorization.dao.repository.AcGroupTypeService;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.vo.group.GroupRes;
import com.bidr.authorization.vo.group.GroupTypeRes;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.config.response.Resp;
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
    private final AcGroupTypeService acGroupTypeService;


    public List<UserGroupTreeRes> getTree(String groupType) {
        List<AcGroup> groups = acGroupService.getGroupByType(groupType);
        return ReflectionUtil.buildTree(UserGroupTreeRes::setChildren, groups, AcGroup::getId, AcGroup::getPid);
    }

    public List<GroupRes> getList(String groupType) {
        List<AcGroup> groups = acGroupService.getGroupByType(groupType);
        return Resp.convert(groups, GroupRes.class);
    }

    public void addGroup(AcGroup req) {
        acGroupService.insert(req);
    }

    public List<GroupTypeRes> getGroupType(String name) {
        List<AcGroupType> groupTypes = acGroupTypeService.getGroupTypeByName(name);
        return Resp.convert(groupTypes, GroupTypeRes.class);
    }
}
