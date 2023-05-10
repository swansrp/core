package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.authorization.dao.mapper.AcGroupDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcGroupService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 16:34
 */
@Service
public class AcGroupService extends BaseSqlRepo<AcGroupDao, AcGroup> {
    public List<AcGroup> getGroupByType(String type) {
        LambdaQueryWrapper<AcGroup> wrapper = super.getQueryWrapper().eq(AcGroup::getType, type);
        return super.select(wrapper);
    }
}
