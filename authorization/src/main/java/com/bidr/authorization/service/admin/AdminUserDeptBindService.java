package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.vo.department.*;
import com.bidr.authorization.vo.user.UserRes;
import com.bidr.kernel.mybatis.repository.BaseBindRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 部门用户绑定服务
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class AdminUserDeptBindService extends BaseBindRepo<AcDept, AcUserDept, AcUser, DepartmentItem, DepartmentAccountRes> {

    private final AcUserDeptService acUserDeptService;
    private final AcUserService acUserService;

    /**
     * 更新用户部门关系
     *
     * @param acUserDept 用户部门关系
     */
    public void updateAcUserDept(AcUserDept acUserDept) {
        acUserDeptService.updateById(acUserDept);
    }

    /**
     * 批量绑定用户到部门（带数据权限）
     *
     * @param req 批量绑定请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindList(BindDeptUserListDataScopeReq req) {
        if (FuncUtil.isNotEmpty(req.getAttachIdList())) {
            for (Object attachId : req.getAttachIdList()) {
                AcUserDept acUserDept = buildBindEntity(attachId, req.getEntityId());
                acUserDept.setDataScope(req.getDataScope());
                acUserDeptService.insertOrUpdate(acUserDept);
            }
        }
    }

    /**
     * 绑定用户到部门（带数据权限）
     *
     * @param req 绑定请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void bind(BindDeptUserDataScopeReq req) {
        AcUserDept acUserDept = buildBindEntity(req.getAttachId(), req.getEntityId());
        acUserDept.setDataScope(req.getDataScope());
        acUserDeptService.insertOrUpdate(acUserDept);
    }

    /**
     * 查询已绑定的用户列表（带查询条件）
     *
     * @param req 查询请求
     * @return 用户列表
     */
    public List<DepartmentAccountRes> searchBindList(BindDeptUserReq req) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.selectFilter(getAttachClass(), f -> !"dept_id".equals(f.getColumn()))
                .select(bindEntityId())
                .leftJoin(getBindClass(), bindAttachId(), attachId())
                .eq(FuncUtil.isNotEmpty(req.getDeptId()), bindEntityId(), req.getDeptId())
                .in(FuncUtil.isNotEmpty(req.getDeptIdList()), bindEntityId(), req.getDeptIdList())
                .like(FuncUtil.isNotEmpty(req.getName()), AcUser::getName, req.getName())
                .eq(FuncUtil.isNotEmpty(req.getDataScope()), AcUserDept::getDataScope, req.getDataScope());
        return attachRepo().selectJoinList(DepartmentAccountRes.class, wrapper);
    }

    /**
     * 根据部门ID获取用户列表
     *
     * @param deptId 部门ID
     * @return 用户列表
     */
    public List<DepartmentAccountRes> getUserListByDeptId(String deptId) {
        MPJLambdaWrapper<AcUser> wrapper = new MPJLambdaWrapper<>(getAttachClass());
        wrapper.selectFilter(getAttachClass(), f -> !"dept_id".equals(f.getColumn()))
                .select(bindEntityId())
                .leftJoin(getBindClass(), bindAttachId(), attachId())
                .leftJoin(getEntityClass(), entityId(), bindEntityId())
                .eq(AcDept::getDeptId, deptId);
        return new ArrayList<>(distinct(attachRepo().selectJoinList(DepartmentAccountRes.class, wrapper)));
    }

    /**
     * 去重用户列表
     *
     * @param selectJoinList 查询结果列表
     * @return 去重后的用户列表
     */
    private Collection<DepartmentAccountRes> distinct(List<DepartmentAccountRes> selectJoinList) {
        Map<String, DepartmentAccountRes> filter = new LinkedHashMap<>();
        if (FuncUtil.isNotEmpty(selectJoinList)) {
            for (DepartmentAccountRes user : selectJoinList) {
                filter.put(user.getCustomerNumber(), ReflectionUtil.copy(user, DepartmentAccountRes.class));
            }
        }
        return filter.values();
    }

    @Override
    protected SFunction<AcUserDept, ?> bindEntityId() {
        return AcUserDept::getDeptId;
    }

    @Override
    protected SFunction<AcUserDept, ?> bindAttachId() {
        return AcUserDept::getUserId;
    }

    @Override
    protected SFunction<AcUser, ?> attachId() {
        return AcUser::getUserId;
    }

    @Override
    protected SFunction<AcDept, ?> entityId() {
        return AcDept::getDeptId;
    }
}
