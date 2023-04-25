package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.mapper.AcDeptDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcDeptService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:02
 */
@Service
public class AcDeptService extends BaseSqlRepo<AcDeptDao, AcDept> {
    @Override
    public void truncate() {
        super.baseMapper.truncate();
    }

    public List<AcDept> getDepartmentByStatus(Integer status) {
        LambdaQueryWrapper<AcDept> wrapper = super.getQueryWrapper().eq(AcDept::getStatus, status)
                .orderByAsc(AcDept::getShowOrder);
        return select(wrapper);
    }
}





