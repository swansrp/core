package com.bidr.neo4j.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoRelationProperty;
import com.bidr.neo4j.dao.mapper.NeoRelationPropertyMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: NeoRelationPropertyService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 17:09
 */
@Service
public class NeoRelationPropertyService extends BaseSqlRepo<NeoRelationPropertyMapper, NeoRelationProperty> {

    public List<NeoRelationProperty> getRelationshipProperties(Long relationshipId) {
        LambdaQueryWrapper<NeoRelationProperty> wrapper = super.getQueryWrapper()
                .eq(NeoRelationProperty::getRelationId, relationshipId);
        return super.select(wrapper);
    }
}
