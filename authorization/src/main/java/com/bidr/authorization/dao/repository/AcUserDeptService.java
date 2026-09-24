package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.mapper.AcUserDeptDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Title: AcUserDeptService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 10:27
 */
@Service
public class AcUserDeptService extends BaseSqlRepo<AcUserDeptDao, AcUserDept> {

    /**
     * 查询用户的全部部门关系。
     * <p>
     * ac_user_dept 是 (user_id, dept_id) 复合主键，一个用户可同时归属多个部门，
     * 故不能用 selectOne——多部门时 MyBatis 会抛 TooManyResultsException。
     *
     * @param userId 用户id
     * @return 部门关系列表（含各自的 dataScope），无数据时返回空列表
     */
    public List<AcUserDept> listByUserId(Long userId) {
        LambdaQueryWrapper<AcUserDept> wrapper = super.getQueryWrapper().eq(AcUserDept::getUserId, userId);
        return select(wrapper);
    }

    /**
     * 查询用户的第一条部门关系。
     * <p>
     * 用户可属多部门时取哪条是不确定的，需要完整部门范围请用 {@link #listByUserId(Long)}。
     */
    public AcUserDept getByUserId(Long userId) {
        List<AcUserDept> userDepts = listByUserId(userId);
        return FuncUtil.isEmpty(userDepts) ? null : userDepts.get(0);
    }

    public void deleteByDeptId(String deptId) {
        LambdaQueryWrapper<AcUserDept> wrapper = super.getQueryWrapper().eq(AcUserDept::getDeptId, deptId);
        delete(wrapper);
    }

    public void deleteByUserId(@NotNull(message = "用户ID不能为null") Long userId) {
        LambdaQueryWrapper<AcUserDept> wrapper = super.getQueryWrapper().eq(AcUserDept::getUserId, userId);
        delete(wrapper);
    }
}
