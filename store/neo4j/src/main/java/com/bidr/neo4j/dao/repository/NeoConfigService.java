package com.bidr.neo4j.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.neo4j.dao.entity.NeoConfig;
import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoRelation;
import com.bidr.neo4j.dao.mapper.NeoConfigMapper;
import com.bidr.neo4j.vo.configuration.RelationshipRes;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: NeoConfigService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/07 14:59
 */
@Service
public class NeoConfigService extends BaseSqlRepo<NeoConfigMapper, NeoConfig> {

    private static final String START = "s";
    private static final String END = "e";

    public List<RelationshipRes> getRelationshipByNodeId(Long nodeId) {
        MPJLambdaWrapper<NeoConfig> wrapper = new MPJLambdaWrapper<>();
        wrapper.distinct().leftJoin(NeoRelation.class, NeoRelation::getId, NeoConfig::getRelationId)
                .leftJoin(NeoNode.class, START, NeoNode::getId, NeoConfig::getStartId)
                .leftJoin(NeoNode.class, END, NeoNode::getId, NeoConfig::getEndId)
                .selectAs(START, NeoNode::getId, RelationshipRes::getStartId)
                .selectAs(START, NeoNode::getLabel, RelationshipRes::getStartNode)
                .selectAs(END, NeoNode::getId, RelationshipRes::getEndId)
                .selectAs(END, NeoNode::getLabel, RelationshipRes::getEndNode)
                .selectAs(NeoRelation::getId, RelationshipRes::getRelationId)
                .selectAs(NeoRelation::getType, RelationshipRes::getName);
        wrapper.eq(NeoConfig::getStartId, nodeId).or(w -> w.eq(NeoConfig::getEndId, nodeId));
        return super.selectJoinList(RelationshipRes.class, wrapper);
    }
}
