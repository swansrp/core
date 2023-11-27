package com.bidr.neo4j.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoNodeProperty;
import com.bidr.neo4j.dao.mapper.NeoNodePropertyMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: NeoNodePropertyService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 11:06
 */
@Service
public class NeoNodePropertyService extends BaseSqlRepo<NeoNodePropertyMapper, NeoNodeProperty> {

    public List<NeoNodeProperty> getNodeProperties(Long id) {
        LambdaQueryWrapper<NeoNodeProperty> wrapper = super.getQueryWrapper().eq(NeoNodeProperty::getNodeId, id);
        return super.select(wrapper);
    }

    public List<NeoNodeProperty> getNodeIndexPropertyByNodeId(Long id) {
        LambdaQueryWrapper<NeoNodeProperty> wrapper = super.getQueryWrapper().eq(NeoNodeProperty::getNodeId, id)
                .eq(NeoNodeProperty::getIndex, CommonConst.YES);
        return super.select(wrapper);
    }
}
