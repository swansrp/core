package com.bidr.neo4j.vo.configuration;

import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoRelation;
import com.diboot.core.binding.annotation.BindField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: RelationshipRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/14 08:41
 */
@Data
public class RelationshipRes {
    @ApiModelProperty("关系id")
    private Long relationId;
    @BindField(entity = NeoRelation.class, condition = "this.relationId = id", field = "type")
    @ApiModelProperty("关系名称")
    private String name;
    @ApiModelProperty("起始节点id")
    private Long startId;
    @BindField(entity = NeoNode.class, condition = "this.startId = id", field = "label")
    @ApiModelProperty("起始节点名称")
    private String startNode;
    @ApiModelProperty("终止节点id")
    private Long endId;
    @BindField(entity = NeoNode.class, condition = "this.endId = id", field = "label")
    @ApiModelProperty("终止节点名称")
    private String endNode;


}
