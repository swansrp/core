package com.bidr.neo4j.vo.configuration;

import com.bidr.kernel.config.response.BindRepo;
import com.bidr.neo4j.dao.entity.NeoNode;
import com.bidr.neo4j.dao.entity.NeoRelation;
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
    @BindRepo(entity = NeoRelation.class, matchField = "id", extractField = "type", sourceField = "relationId")
    @ApiModelProperty("关系名称")
    private String name;
    @ApiModelProperty("起始节点id")
    private Long startId;
    @BindRepo(entity = NeoNode.class, matchField = "id", extractField = "label", sourceField = "startId")
    @ApiModelProperty("起始节点名称")
    private String startNode;
    @ApiModelProperty("终止节点id")
    private Long endId;
    @BindRepo(entity = NeoNode.class, matchField = "id", extractField = "label", sourceField = "endId")
    @ApiModelProperty("终止节点名称")
    private String endNode;


}
