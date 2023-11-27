package com.bidr.neo4j.bo;

import com.bidr.neo4j.dao.entity.NeoConfigProperty;
import com.bidr.neo4j.dao.entity.NeoRelationProperty;
import com.diboot.core.binding.annotation.BindEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: RelationPropertyBO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 18:18
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RelationConfigPropertyBO extends NeoConfigProperty {

    @BindEntity(entity = NeoRelationProperty.class, condition = "this.relationPropertyId = id")
    private NeoRelationProperty property;
}
