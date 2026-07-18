package com.bidr.authorization.service.group;

import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.authorization.dao.repository.AcGroupService;
import com.bidr.authorization.dao.repository.AcGroupTypeService;
import com.bidr.authorization.dao.repository.AcUserGroupService;
import com.bidr.authorization.dao.repository.join.AcUserGroupJoinService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.vo.group.GroupRes;
import com.bidr.authorization.vo.group.GroupTypeRes;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
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
    private final AcUserGroupJoinService acUserGroupJoinService;


    public List<UserGroupTreeRes> getTree(String groupType) {
        List<AcGroup> groups = acGroupService.getGroupByType(groupType);
        return ReflectionUtil.buildTree(UserGroupTreeRes::setChildren, groups, AcGroup::getId, AcGroup::getPid);
    }

    public List<UserGroupTreeRes> getGroupTreeByUser(String groupType) {
        Long userId = AccountContext.getUserId();
        AcGroup groupRoot = acGroupService.getGroupRoot(groupType);
        List<AcGroup> groups = acUserGroupJoinService.getAcGroupByUserIdAndGroupType(userId, groupType);
        groups.add(groupRoot);
        return ReflectionUtil.buildTree(UserGroupTreeRes::setChildren, groups, AcGroup::getId, AcGroup::getPid);
    }

    public List<UserGroupTreeRes> getGroupTreeByUserDataScope(String groupType) {
        String customerNumber = AccountContext.getOperator();
        List<AcGroup> groups = acUserGroupJoinService.getGroupListFromDataScope(customerNumber, groupType);
        return ReflectionUtil.buildTree(UserGroupTreeRes::getChildren, groups, AcGroup::getId, AcGroup::getPid);
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

    /**
     * 新增用户组类型
     */
    @Transactional(rollbackFor = Exception.class)
    public void addGroupType(AcGroupType req) {
        Validator.assertTrue(!acGroupTypeService.existedById(req.getId()),
                ErrCodeSys.PA_DATA_HAS_EXIST, "用户组类型");
        acGroupTypeService.insert(req);
    }

    /**
     * 删除用户组类型
     * <p>
     * 校验：该类型下不存在任何用户组时才允许删除，避免产生孤儿用户组数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroupType(String id) {
        long groupCount = acGroupService.countGroupByType(id);
        Validator.assertTrue(groupCount == 0, ErrCodeSys.SYS_ERR_MSG,
                "该类型下存在 " + groupCount + " 个用户组，无法删除");
        acGroupTypeService.deleteById(id);
    }

    public void deleteGroup(String id) {
        boolean existedUserInGroup = acUserGroupService.existedByGroupId(Long.parseLong(id));
        Validator.assertFalse(existedUserInGroup, AC_ROLE_HAS_USER);
        acGroupService.deleteById(id);
    }
}
