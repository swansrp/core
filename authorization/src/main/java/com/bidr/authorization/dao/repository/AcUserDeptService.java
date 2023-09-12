package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.mapper.AcUserDeptDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

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
}
