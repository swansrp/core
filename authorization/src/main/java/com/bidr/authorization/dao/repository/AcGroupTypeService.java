package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.authorization.dao.mapper.AcGroupTypeDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcGroupTypeService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/08 09:47
 */
@Service
public class AcGroupTypeService extends BaseSqlRepo<AcGroupTypeDao, AcGroupType> {

    public List<AcGroupType> getGroupTypeByName(String name) {
        LambdaQueryWrapper<AcGroupType> wrapper = super.getQueryWrapper()
                .like(FuncUtil.isNotEmpty(name), AcGroupType::getName, name);
        return super.select(wrapper);
    }
}
