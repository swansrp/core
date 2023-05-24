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
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.bidr.authorization.constants.err.AccountErrCode.AC_ROLE_HAS_USER;

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

    private final AcGroupService acGroupService;
    private final AcGroupTypeService acGroupTypeService;

    private final AcUserGroupService acUserGroupService;


    public List<UserGroupTreeRes> getTree(String groupType) {
        List<AcGroup> groups = acGroupService.getGroupByType(groupType);

        return ReflectionUtil.buildTree(UserGroupTreeRes::setChildren, groups, AcGroup::getId, AcGroup::getPid);
    }

    public List<GroupRes> getList(String groupType) {
        List<AcGroup> groups = acGroupService.getGroupByType(groupType);
        return Resp.convert(groups, GroupRes.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addGroup(AcGroup req) {
        long size = acGroupService.countGroupByType(req.getType());
        req.setDisplayOrder((int) size + 1);
        acGroupService.insert(req);
        req.setKey(req.getId());
        acGroupService.updateById(req);
    }

    public List<GroupTypeRes> getGroupType(String name) {
        List<AcGroupType> groupTypes = acGroupTypeService.getGroupTypeByName(name);
        return Resp.convert(groupTypes, GroupTypeRes.class);
    }

    public void deleteGroup(String id) {
        boolean existedUserInGroup = acUserGroupService.existedByGroupId(Long.parseLong(id));
        Validator.assertFalse(existedUserInGroup, AC_ROLE_HAS_USER);
        acGroupService.deleteById(id);
    }
}
