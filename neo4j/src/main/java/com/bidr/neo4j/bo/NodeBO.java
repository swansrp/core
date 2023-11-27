package com.bidr.neo4j.bo;

import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoNodeSync;
import com.diboot.core.binding.annotation.BindEntityList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: NodeBO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/06 11:35
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NodeBO extends NeoNode {
    @BindEntityList(entity = NeoNodeSync.class, condition = "this.id = node_id", deepBind = true)
    private List<NodeSyncBO> syncProperties;
}
