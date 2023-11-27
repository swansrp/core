package com.bidr.neo4j.bo;

import com.bidr.neo4j.dao.entity.NeoConfig;
import com.bidr.neo4j.dao.entity.NeoConfigProperty;
import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoRelation;
import com.diboot.core.binding.annotation.BindEntity;
import com.diboot.core.binding.annotation.BindEntityList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: RelationConfigBO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 17:24
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RelationConfigBO extends NeoConfig {
    @BindEntity(entity = NeoNode.class, condition = "this.startId = id")
    private NeoNode startNode;
    @BindEntity(entity = NeoNode.class, condition = "this.endId = id")
    private NeoNode endNode;
    @BindEntityList(entity = NeoNode.class, condition = "this.extraNodeList = id", splitBy = ",")
    private List<NeoNode> extraNeoNodeList;
    @BindEntity(entity = NeoRelation.class, condition = "this.relationId = id")
    private NeoRelation relation;

    @BindEntityList(entity = NeoConfigProperty.class, condition = "this.id = config_id", deepBind = true)
    private List<RelationConfigPropertyBO> relationProperties;
}
