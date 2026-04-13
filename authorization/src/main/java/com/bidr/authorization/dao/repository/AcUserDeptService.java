package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.mapper.AcUserDeptDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;

/**
 * Title: AcUserDeptService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 10:27
 */
@Service
public class AcUserDeptService extends BaseSqlRepo<AcUserDeptDao, AcUserDept> {

    public AcUserDept getByUserId(Long userId) {
        LambdaQueryWrapper<AcUserDept> wrapper = super.getQueryWrapper().eq(AcUserDept::getUserId, userId);
        return selectOne(wrapper);
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
