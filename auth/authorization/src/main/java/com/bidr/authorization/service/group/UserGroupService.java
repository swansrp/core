package com.bidr.authorization.service.group;

import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserGroup;
import com.bidr.authorization.dao.repository.*;
import com.bidr.authorization.dao.repository.join.AcUserGroupJoinService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.vo.group.GroupRes;
import com.bidr.authorization.vo.group.GroupTransferVO;
import com.bidr.authorization.vo.group.GroupTypeRes;
import com.bidr.authorization.vo.group.UserGroupTreeRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    private final AcGroupMenuService acGroupMenuService;

    private final AcUserGroupService acUserGroupService;
    private final AcUserGroupJoinService acUserGroupJoinService;
    private final AcUserService acUserService;


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
        List<AcGroup> groups = (groupType == null || groupType.isEmpty())
                ? acGroupService.getAllGroups()
                : acGroupService.getGroupByType(groupType);
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

    /**
     * 导出用户组类型：类型信息 + 该类型下整棵用户组树（父子关系与同级顺序由嵌套结构表达），
     * 组上绑定的用户按工号携带（user_id 各环境自增不可直接搬运）
     */
    public GroupTransferVO exportGroupType(String typeId) {
        Validator.assertNotBlank(typeId, ErrCodeSys.PA_PARAM_NULL, "用户组类型id");
        AcGroupType type = acGroupTypeService.getById(typeId);
        Validator.assertNotNull(type, ErrCodeSys.PA_DATA_NOT_EXIST, "用户组类型");

        List<AcGroup> groups = acGroupService.getGroupByType(typeId);
        GroupTransferVO res = new GroupTransferVO();
        res.setTypeId(type.getId());
        res.setTypeName(type.getName());
        res.setGroups(buildTransferTree(groups, loadUserBinds(groups)));
        return res;
    }

    private Map<Long, List<GroupTransferVO.UserBind>> loadUserBinds(List<AcGroup> groups) {
        Map<Long, List<GroupTransferVO.UserBind>> usersByGroupId = new HashMap<>();
        if (FuncUtil.isEmpty(groups)) {
            return usersByGroupId;
        }
        List<Long> groupIds = groups.stream().map(AcGroup::getId).collect(Collectors.toList());
        List<AcUserGroup> binds = acUserGroupService.selectByGroupIds(groupIds);
        if (FuncUtil.isEmpty(binds)) {
            return usersByGroupId;
        }
        Set<Long> userIds = binds.stream().map(AcUserGroup::getUserId).collect(Collectors.toSet());
        Map<Long, AcUser> userById = acUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(AcUser::getUserId, user -> user));
        for (AcUserGroup bind : binds) {
            AcUser user = userById.get(bind.getUserId());
            if (user == null) {
                continue;
            }
            GroupTransferVO.UserBind item = new GroupTransferVO.UserBind();
            item.setCustomerNumber(user.getCustomerNumber());
            item.setUserName(FuncUtil.isNotEmpty(user.getName()) ? user.getName() : user.getUserName());
            item.setDataScope(bind.getDataScope());
            DataPermitScopeDict dict = bind.getDataScope() == null ? null : DataPermitScopeDict.of(bind.getDataScope());
            item.setDataScopeName(dict == null ? null : dict.getLabel());
            usersByGroupId.computeIfAbsent(bind.getGroupId(), key -> new ArrayList<>()).add(item);
        }
        return usersByGroupId;
    }

    private List<GroupTransferVO.Node> buildTransferTree(List<AcGroup> groups,
                                                         Map<Long, List<GroupTransferVO.UserBind>> usersByGroupId) {
        Map<Long, GroupTransferVO.Node> nodeById = new HashMap<>();
        for (AcGroup group : groups) {
            GroupTransferVO.Node node = new GroupTransferVO.Node();
            node.setName(group.getName());
            node.setUsers(usersByGroupId.getOrDefault(group.getId(), new ArrayList<>()));
            nodeById.put(group.getId(), node);
        }
        List<GroupTransferVO.Node> roots = new ArrayList<>();
        for (AcGroup group : groups) {
            GroupTransferVO.Node node = nodeById.get(group.getId());
            GroupTransferVO.Node parent = group.getPid() == null ? null : nodeById.get(group.getPid());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /**
     * 导入用户组类型（跨环境迁移入口）
     * <p>
     * 替换语义：该类型下原有用户组整体删除（连带清掉成员绑定与菜单权限绑定），再整棵导入；
     * 用户组批量走 insertList，插入后按 (type,name) 唯一键取回新 id 回填 key 与 pid；
     * 组上用户按工号翻译为本环境 user_id 后批量插入，查不到的工号跳过并在提示里报告。
     *
     * @return 提示信息（含替换、清绑定与绑定用户的数量，供前端原样提示）
     */
    @Transactional(rollbackFor = Exception.class)
    public String importGroupType(GroupTransferVO req) {
        Validator.assertNotNull(req, ErrCodeSys.PA_PARAM_NULL, "导入内容");
        Validator.assertNotBlank(req.getTypeId(), ErrCodeSys.PA_PARAM_NULL, "用户组类型id");
        Validator.assertNotBlank(req.getTypeName(), ErrCodeSys.PA_PARAM_NULL, "用户组类型名称");
        Validator.assertTrue(req.getTypeId().length() <= 50, ErrCodeSys.SYS_ERR_MSG, "用户组类型id最大长度要小于 50");
        Validator.assertTrue(req.getTypeName().length() <= 50, ErrCodeSys.SYS_ERR_MSG, "用户组类型名称最大长度要小于 50");
        Validator.assertNotEmpty(req.getGroups(), ErrCodeSys.SYS_ERR_MSG, "导入文件中没有用户组");

        List<GroupTransferVO.Node> flat = new ArrayList<>();
        List<String> parentNames = new ArrayList<>();
        List<Integer> displayOrders = new ArrayList<>();
        collectImportNodes(req.getGroups(), null, flat, parentNames, displayOrders, new HashSet<>());

        boolean typeExisted = acGroupTypeService.existedById(req.getTypeId());
        long oldGroupCount = 0;
        long removedUserBinds = 0;
        List<Long> oldGroupIds = acGroupService.getGroupByType(req.getTypeId()).stream()
                .map(AcGroup::getId).collect(Collectors.toList());
        if (FuncUtil.isNotEmpty(oldGroupIds)) {
            oldGroupCount = oldGroupIds.size();
            removedUserBinds = acUserGroupService.countByGroupIds(oldGroupIds);
            acUserGroupService.deleteByGroupIds(oldGroupIds);
            acGroupMenuService.deleteByGroupIds(oldGroupIds);
        }
        acGroupService.deleteByType(req.getTypeId());

        AcGroupType type = new AcGroupType();
        type.setId(req.getTypeId());
        type.setName(req.getTypeName());
        if (typeExisted) {
            acGroupTypeService.updateById(type);
        } else {
            acGroupTypeService.insert(type);
        }

        List<AcGroup> inserts = new ArrayList<>();
        for (int i = 0; i < flat.size(); i++) {
            AcGroup group = new AcGroup();
            group.setType(req.getTypeId());
            group.setName(flat.get(i).getName());
            group.setKey(0L);
            group.setDisplayOrder(displayOrders.get(i));
            inserts.add(group);
        }
        acGroupService.insert(inserts);

        Map<String, Long> idByName = acGroupService.getGroupByType(req.getTypeId()).stream()
                .collect(Collectors.toMap(AcGroup::getName, AcGroup::getId));
        List<AcGroup> fixes = new ArrayList<>();
        for (int i = 0; i < flat.size(); i++) {
            AcGroup group = new AcGroup();
            Long id = idByName.get(flat.get(i).getName());
            group.setId(id);
            group.setKey(id);
            if (parentNames.get(i) != null) {
                group.setPid(idByName.get(parentNames.get(i)));
            }
            fixes.add(group);
        }
        acGroupService.updateById(fixes);

        List<AcUserGroup> userBinds = new ArrayList<>();
        Set<String> missedCustomers = new LinkedHashSet<>();
        for (int i = 0; i < flat.size(); i++) {
            GroupTransferVO.Node node = flat.get(i);
            if (FuncUtil.isEmpty(node.getUsers())) {
                continue;
            }
            Long groupId = idByName.get(node.getName());
            Set<String> seenCustomers = new HashSet<>();
            for (GroupTransferVO.UserBind bind : node.getUsers()) {
                if (FuncUtil.isEmpty(bind.getCustomerNumber()) || !seenCustomers.add(bind.getCustomerNumber())) {
                    continue;
                }
                AcUser user = acUserService.getByCustomerNumber(bind.getCustomerNumber());
                if (user == null) {
                    missedCustomers.add(bind.getCustomerNumber());
                    continue;
                }
                AcUserGroup bindRow = new AcUserGroup();
                bindRow.setUserId(user.getUserId());
                bindRow.setGroupId(groupId);
                bindRow.setDataScope(resolveDataScope(bind));
                userBinds.add(bindRow);
            }
        }
        if (FuncUtil.isNotEmpty(userBinds)) {
            acUserGroupService.insert(userBinds);
        }

        String missedMsg = missedCustomers.isEmpty() ? "" : String.format("；%d 个工号在本环境未找到已跳过：%s",
                missedCustomers.size(), String.join("、", missedCustomers.stream()
                        .limit(10).collect(Collectors.toList())));
        if (typeExisted) {
            return String.format("用户组类型「%s」已存在，已替换：删除原 %d 个用户组（清除用户绑定 %d 条），导入 %d 个用户组、绑定用户 %d 个%s",
                    req.getTypeName(), oldGroupCount, removedUserBinds, flat.size(), userBinds.size(), missedMsg);
        }
        return String.format("导入成功：新增用户组类型「%s」，共 %d 个用户组、绑定用户 %d 个%s",
                req.getTypeName(), flat.size(), userBinds.size(), missedMsg);
    }

    private Integer resolveDataScope(GroupTransferVO.UserBind bind) {
        if (bind.getDataScope() != null && DataPermitScopeDict.of(bind.getDataScope()) != null) {
            return bind.getDataScope();
        }
        DataPermitScopeDict byLabel = FuncUtil.isEmpty(bind.getDataScopeName()) ? null
                : DictEnumUtil.getEnum(bind.getDataScopeName(), "label", DataPermitScopeDict.class, null);
        return (byLabel == null ? DataPermitScopeDict.DEPARTMENT : byLabel).getValue();
    }

    private void collectImportNodes(List<GroupTransferVO.Node> nodes, String parentName,
                                    List<GroupTransferVO.Node> flat, List<String> parentNames,
                                    List<Integer> displayOrders, Set<String> nameSet) {
        for (int i = 0; i < nodes.size(); i++) {
            GroupTransferVO.Node node = nodes.get(i);
            Validator.assertNotBlank(node.getName(), ErrCodeSys.PA_PARAM_NULL, "用户组名称");
            Validator.assertTrue(node.getName().length() <= 50, ErrCodeSys.SYS_ERR_MSG,
                    "用户组名称「" + node.getName() + "」最大长度要小于 50");
            Validator.assertTrue(nameSet.add(node.getName()), ErrCodeSys.PA_DATA_HAS_EXIST,
                    "导入文件中用户组名称「" + node.getName() + "」重复");
            flat.add(node);
            parentNames.add(parentName);
            displayOrders.add(i + 1);
            collectImportNodes(node.getChildren(), node.getName(), flat, parentNames, displayOrders, nameSet);
        }
    }
}
